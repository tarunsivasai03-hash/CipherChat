import pytest

from app.extensions import socketio
from app.sockets import registry


def _connect(app, actor):
    return socketio.test_client(app, auth={"token": actor.token})


def _events(client, name):
    return [e["args"][0] for e in client.get_received() if e["name"] == name]


def test_connect_requires_valid_token(app, make_user):
    a = make_user()
    assert not socketio.test_client(app, auth={"token": "garbage"}).is_connected()
    assert not socketio.test_client(app, auth={}).is_connected()
    assert not socketio.test_client(app).is_connected()
    assert not socketio.test_client(app, auth={"token": 12345}).is_connected()
    assert _connect(app, a).is_connected()


def test_revoked_session_cannot_connect_and_is_kicked(app, make_user):
    a = make_user()
    sock = _connect(app, a)
    assert sock.is_connected()
    a.post("/api/v1/auth/logout")
    assert not sock.is_connected()  # server-side disconnect on revoke
    assert not _connect(app, a).is_connected()


def test_join_requires_membership(app, make_user, direct):
    a, b, outsider = make_user(), make_user(), make_user()
    cid = direct(a, b)
    s_out = _connect(app, outsider)
    assert s_out.emit("join_conversation", {"conversation_id": cid}, callback=True) == {"ok": False, "error": "not_found"}
    assert _connect(app, a).emit("join_conversation", {"conversation_id": cid}, callback=True) == {"ok": True}
    assert s_out.emit("join_conversation", {"conversation_id": "1; DROP"}, callback=True)["ok"] is False
    assert s_out.emit("join_conversation", "notadict", callback=True)["ok"] is False


def test_send_relays_to_members_only_and_persists_before_ack(app, make_user, direct):
    a, b, outsider = make_user(), make_user(), make_user()
    cid = direct(a, b)
    sa, sb, so = _connect(app, a), _connect(app, b), _connect(app, outsider)
    ack = sa.emit("send_message", {"conversation_id": cid, "payload": "hi", "client_msg_id": "sock-000001"}, callback=True)
    assert ack["ok"] and ack["status"] == "accepted" and ack["created"]
    assert ack["message"]["sender_id"] == a.id
    got = _events(sb, "new_message")
    assert len(got) == 1 and got[0]["payload"] == "hi"
    assert _events(so, "new_message") == []
    # persisted: visible through HTTP
    assert [m["payload"] for m in b.get(f"/api/v1/conversations/{cid}/messages").get_json()["messages"]] == ["hi"]


def test_socket_duplicate_send_not_rebroadcast(app, make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    sa, sb = _connect(app, a), _connect(app, b)
    data = {"conversation_id": cid, "payload": "hi", "client_msg_id": "sock-dup-001"}
    first = sa.emit("send_message", data, callback=True)
    second = sa.emit("send_message", data, callback=True)
    assert first["created"] and not second["created"]
    assert first["message"]["id"] == second["message"]["id"]
    assert len(_events(sb, "new_message")) == 1


def test_forged_sender_id_ignored(app, make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    ack = _connect(app, a).emit(
        "send_message",
        {"conversation_id": cid, "payload": "x", "client_msg_id": "sock-forge-1", "sender_id": b.id, "user_id": b.id},
        callback=True,
    )
    assert ack["message"]["sender_id"] == a.id


def test_non_member_cannot_send_over_socket(app, make_user, direct):
    a, b, outsider = make_user(), make_user(), make_user()
    cid = direct(a, b)
    ack = _connect(app, outsider).emit("send_message", {"conversation_id": cid, "payload": "x", "client_msg_id": "sock-evil-01"}, callback=True)
    assert ack == {"ok": False, "error": "not_found"}


def test_e2e_over_socket_refused(app, make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    ack = _connect(app, a).emit("send_message", {"conversation_id": cid, "kind": "e2e", "payload": "x", "client_msg_id": "sock-e2e-001"}, callback=True)
    assert ack == {"ok": False, "error": "e2e_unavailable"}


def test_delivery_receipt_flow(app, make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    sa, sb = _connect(app, a), _connect(app, b)
    mid = sa.emit("send_message", {"conversation_id": cid, "payload": "x", "client_msg_id": "sock-rcpt-01"}, callback=True)["message"]["id"]
    assert sa.emit("message_delivered", {"message_id": mid}, callback=True) == {"ok": False, "error": "invalid_target"}  # sender cannot ack own
    _events(sa, "new_message")
    assert sb.emit("message_delivered", {"message_id": mid}, callback=True) == {"ok": True, "recorded": True}
    assert sb.emit("message_delivered", {"message_id": mid}, callback=True) == {"ok": True, "recorded": False}
    receipts = _events(sa, "delivery_receipt")
    assert len(receipts) == 1 and receipts[0]["message_id"] == mid and receipts[0]["user_id"] == b.id
    assert a.get(f"/api/v1/conversations/{cid}/messages").get_json()["messages"][0]["delivered_to"] == [b.id]


def test_outsider_cannot_ack_delivery(app, make_user, direct):
    a, b, outsider = make_user(), make_user(), make_user()
    cid = direct(a, b)
    mid = _connect(app, a).emit("send_message", {"conversation_id": cid, "payload": "x", "client_msg_id": "sock-ack-001"}, callback=True)["message"]["id"]
    assert _connect(app, outsider).emit("message_delivered", {"message_id": mid}, callback=True) == {"ok": False, "error": "not_found"}


def test_typing_only_reaches_joined_members(app, make_user, direct):
    a, b, outsider = make_user(), make_user(), make_user()
    cid = direct(a, b)
    sa, sb, so = _connect(app, a), _connect(app, b), _connect(app, outsider)
    sa.emit("join_conversation", {"conversation_id": cid}, callback=True)
    sb.emit("join_conversation", {"conversation_id": cid}, callback=True)
    so.emit("join_conversation", {"conversation_id": cid}, callback=True)  # refused
    assert sa.emit("typing", {"conversation_id": cid, "is_typing": True}, callback=True) == {"ok": True}
    assert _events(sb, "typing") == [{"conversation_id": cid, "user_id": a.id, "is_typing": True}]
    assert _events(so, "typing") == []
    assert so.emit("typing", {"conversation_id": cid}, callback=True) == {"ok": False, "error": "not_found"}


def test_removed_group_member_stops_receiving(app, make_user, befriend):
    a, b = make_user(), make_user()
    befriend(a, b)
    gid = a.post("/api/v1/conversations", {"type": "group", "name": "G", "member_ids": [b.id]}).get_json()["conversation"]["id"]
    sa, sb = _connect(app, a), _connect(app, b)
    sa.emit("join_conversation", {"conversation_id": gid}, callback=True)
    sb.emit("join_conversation", {"conversation_id": gid}, callback=True)
    a.delete(f"/api/v1/conversations/{gid}/members/{b.id}")
    _events(sb, "conversation_updated")
    sa.emit("typing", {"conversation_id": gid}, callback=True)
    sa.emit("send_message", {"conversation_id": gid, "payload": "secret", "client_msg_id": "sock-grp-001"}, callback=True)
    assert _events(sb, "typing") == [] and _events(sb, "new_message") == []


def test_socket_rate_limit(app, make_user, direct):
    app.config["SOCKET_SEND_LIMIT"] = 3
    a, b = make_user(), make_user()
    cid = direct(a, b)
    sa = _connect(app, a)
    results = [
        sa.emit("send_message", {"conversation_id": cid, "payload": "x", "client_msg_id": f"sock-rl-{i:04d}"}, callback=True)
        for i in range(5)
    ]
    assert [r["ok"] for r in results] == [True, True, True, False, False]
    assert results[-1]["error"] == "rate_limited"


def test_expired_session_event_rejected(app, make_user, direct):
    from datetime import timedelta

    from app.extensions import db
    from app.models import AuthSession
    from app.utils import utcnow

    a, b = make_user(), make_user()
    cid = direct(a, b)
    sa = _connect(app, a)
    with app.app_context():
        s = db.session.query(AuthSession).filter_by(user_id=a.id).first()
        s.expires_at = utcnow() - timedelta(seconds=1)
        db.session.commit()
    ack = sa.emit("send_message", {"conversation_id": cid, "payload": "x", "client_msg_id": "sock-exp-001"}, callback=True)
    assert ack == {"ok": False, "error": "unauthorized"}
