from datetime import timedelta

from app.extensions import db
from app.models import Message
from app.utils import utcnow


def test_direct_requires_contact_and_is_idempotent(make_user, befriend):
    a, b = make_user(), make_user()
    assert a.post("/api/v1/conversations", {"type": "direct", "user_id": b.id}).status_code == 403
    befriend(a, b)
    first = a.post("/api/v1/conversations", {"type": "direct", "user_id": b.id})
    again = b.post("/api/v1/conversations", {"type": "direct", "user_id": a.id})
    assert first.status_code == 201 and again.status_code == 200
    assert first.get_json()["conversation"]["id"] == again.get_json()["conversation"]["id"]
    assert first.get_json()["conversation"]["encryption"] == {"e2e": False, "state": "unavailable"}


def test_send_list_and_pagination(make_user, direct, send):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    for i in range(7):
        assert send(a if i % 2 == 0 else b, cid, f"m{i}").status_code == 201
    page1 = a.get(f"/api/v1/conversations/{cid}/messages?limit=3").get_json()
    assert [m["payload"] for m in page1["messages"]] == ["m4", "m5", "m6"] and page1["has_more"]
    page2 = a.get(f"/api/v1/conversations/{cid}/messages?limit=3&before={page1['next_before']}").get_json()
    assert [m["payload"] for m in page2["messages"]] == ["m1", "m2", "m3"] and page2["has_more"]
    page3 = a.get(f"/api/v1/conversations/{cid}/messages?limit=3&before={page2['next_before']}").get_json()
    assert [m["payload"] for m in page3["messages"]] == ["m0"] and not page3["has_more"]


def test_duplicate_client_msg_id_is_idempotent(make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    body = {"kind": "plain", "payload": "once", "client_msg_id": "dup-0000001"}
    r1 = a.post(f"/api/v1/conversations/{cid}/messages", body)
    r2 = a.post(f"/api/v1/conversations/{cid}/messages", body)
    assert r1.status_code == 201 and r2.status_code == 200
    assert r1.get_json()["message"]["id"] == r2.get_json()["message"]["id"]
    assert len(a.get(f"/api/v1/conversations/{cid}/messages").get_json()["messages"]) == 1


def test_cipher_messages_require_cipher_name(make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    bad = a.post(f"/api/v1/conversations/{cid}/messages", {"kind": "cipher", "payload": "Khoor", "client_msg_id": "c-00000001"})
    ok = a.post(f"/api/v1/conversations/{cid}/messages", {"kind": "cipher", "payload": "Khoor", "meta": "caesar", "client_msg_id": "c-00000002"})
    assert bad.status_code == 400 and ok.status_code == 201


def test_message_validation(make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    url = f"/api/v1/conversations/{cid}/messages"
    assert a.post(url, {"kind": "plain", "payload": "", "client_msg_id": "x-00000001"}).status_code == 400
    assert a.post(url, {"kind": "plain", "payload": "x" * 5000, "client_msg_id": "x-00000002"}).status_code == 400
    assert a.post(url, {"kind": "plain", "payload": "hi", "client_msg_id": "short"}).status_code == 400
    assert a.post(url, {"kind": "weird", "payload": "hi", "client_msg_id": "x-00000003"}).status_code == 400
    assert a.post(url, {"kind": "plain", "payload": "hi", "client_msg_id": "x-00000004", "reply_to_id": 424242}).status_code == 400


def test_reply_and_reactions(make_user, direct, send):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    parent = send(a, cid, "question").get_json()["message"]
    reply = send(b, cid, "answer", reply_to_id=parent["id"]).get_json()["message"]
    assert reply["reply_to_id"] == parent["id"]
    r = b.put(f"/api/v1/messages/{parent['id']}/reaction", {"emoji": "👍"})
    assert r.get_json()["reactions"] == [{"user_id": b.id, "emoji": "👍"}]
    assert b.put(f"/api/v1/messages/{parent['id']}/reaction", {"emoji": "abc"}).status_code == 400
    assert b.delete(f"/api/v1/messages/{parent['id']}/reaction").get_json()["reactions"] == []


def test_delete_message_only_by_sender(make_user, direct, send):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    mid = send(a, cid).get_json()["message"]["id"]
    assert b.delete(f"/api/v1/messages/{mid}").status_code == 403
    assert a.delete(f"/api/v1/messages/{mid}").status_code == 204
    assert a.get(f"/api/v1/conversations/{cid}/messages").get_json()["messages"] == []


def test_unread_counts_and_seen_marker(make_user, direct, send):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    send(a, cid, "1")
    last = send(a, cid, "2").get_json()["message"]["id"]
    conv_b = b.get("/api/v1/conversations").get_json()["conversations"][0]
    conv_a = a.get("/api/v1/conversations").get_json()["conversations"][0]
    assert conv_b["unread_count"] == 2 and conv_a["unread_count"] == 0
    assert conv_b["last_message"]["payload"] == "2"
    assert b.post(f"/api/v1/conversations/{cid}/seen", {"message_id": last}).status_code == 204
    assert b.get("/api/v1/conversations").get_json()["conversations"][0]["unread_count"] == 0


def test_blocking_stops_messages_in_existing_thread(make_user, direct, send):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    assert send(a, cid).status_code == 201
    a.post("/api/v1/blocks", {"user_id": b.id})
    assert send(a, cid).status_code == 403
    assert send(b, cid).status_code == 403


def test_removing_contact_stops_messages(make_user, direct, send):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    a.delete(f"/api/v1/contacts/{b.id}")
    assert send(b, cid).status_code == 403


def test_disappearing_messages_expire(app, make_user, direct, send):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    assert a.patch(f"/api/v1/conversations/{cid}", {"disappear_after_seconds": 7}).status_code == 400  # not an allowed timer
    assert a.patch(f"/api/v1/conversations/{cid}", {"disappear_after_seconds": 10}).status_code == 200
    msg = send(a, cid, "vanishing").get_json()["message"]
    assert msg["expires_at"] is not None
    assert len(b.get(f"/api/v1/conversations/{cid}/messages").get_json()["messages"]) == 1
    with app.app_context():
        m = db.session.get(Message, msg["id"])
        m.expires_at = utcnow() - timedelta(seconds=1)
        db.session.commit()
    assert b.get(f"/api/v1/conversations/{cid}/messages").get_json()["messages"] == []
    with app.app_context():
        assert db.session.get(Message, msg["id"]) is None  # physically purged
    assert a.patch(f"/api/v1/conversations/{cid}", {"disappear_after_seconds": None}).status_code == 200
    assert send(a, cid, "stays").get_json()["message"]["expires_at"] is None


def test_e2e_kind_is_refused_not_stored(app, make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    r = a.post(f"/api/v1/conversations/{cid}/messages", {"kind": "e2e", "payload": "AAAA", "client_msg_id": "e-00000001"})
    assert r.status_code == 422 and r.get_json()["error"]["code"] == "e2e_unavailable"
    with app.app_context():
        assert db.session.query(Message).count() == 0


# ---- groups -------------------------------------------------------------------------------------

def _group(a, members, name="Crew"):
    r = a.post("/api/v1/conversations", {"type": "group", "name": name, "member_ids": [m.id for m in members]})
    assert r.status_code == 201, r.get_json()
    return r.get_json()["conversation"]["id"]


def test_group_create_requires_contacts(make_user, befriend):
    a, b, c = make_user(), make_user(), make_user()
    befriend(a, b)
    assert a.post("/api/v1/conversations", {"type": "group", "name": "G", "member_ids": [b.id, c.id]}).status_code == 403
    gid = _group(a, [b])
    conv = a.get(f"/api/v1/conversations/{gid}").get_json()["conversation"]
    assert {m["user"]["id"]: m["role"] for m in conv["members"]} == {a.id: "admin", b.id: "member"}


def test_group_admin_permissions_and_leave(make_user, befriend, send):
    a, b, c = make_user(), make_user(), make_user()
    befriend(a, b)
    befriend(a, c)
    gid = _group(a, [b])
    assert b.post(f"/api/v1/conversations/{gid}/members", {"member_ids": [c.id]}).status_code == 403  # non-admin
    assert a.post(f"/api/v1/conversations/{gid}/members", {"member_ids": [c.id]}).status_code == 200
    assert send(c, gid, "hi group").status_code == 201
    assert b.delete(f"/api/v1/conversations/{gid}/members/{c.id}").status_code == 403
    assert a.delete(f"/api/v1/conversations/{gid}/members/{c.id}").status_code == 204
    assert send(c, gid).status_code == 404  # removed members lose access
    assert c.get(f"/api/v1/conversations/{gid}/messages").status_code == 404
    assert a.delete(f"/api/v1/conversations/{gid}/members/{a.id}").status_code == 204  # admin leaves
    conv = b.get(f"/api/v1/conversations/{gid}").get_json()["conversation"]
    assert [m["role"] for m in conv["members"]] == ["admin"]  # remaining member promoted


def test_new_group_member_cannot_read_history(make_user, befriend, send):
    a, b, c = make_user(), make_user(), make_user()
    befriend(a, b)
    befriend(a, c)
    gid = _group(a, [b])
    send(a, gid, "before c joined")
    import time

    time.sleep(1.1)  # created_at has second precision when compared to joined_at
    a.post(f"/api/v1/conversations/{gid}/members", {"member_ids": [c.id]})
    send(a, gid, "after c joined")
    payloads = [m["payload"] for m in c.get(f"/api/v1/conversations/{gid}/messages").get_json()["messages"]]
    assert payloads == ["after c joined"]


def test_group_rename_admin_only(make_user, befriend):
    a, b = make_user(), make_user()
    befriend(a, b)
    gid = _group(a, [b])
    assert b.patch(f"/api/v1/conversations/{gid}", {"name": "Hijack"}).status_code == 403
    assert a.patch(f"/api/v1/conversations/{gid}", {"name": "Renamed"}).get_json()["conversation"]["name"] == "Renamed"


def test_reports(make_user, direct, send):
    a, b, c = make_user(), make_user(), make_user()
    cid = direct(a, b)
    mid = send(a, cid, "rude").get_json()["message"]["id"]
    assert b.post("/api/v1/reports", {"message_id": mid, "reason": "abusive"}).status_code == 201
    assert c.post("/api/v1/reports", {"message_id": mid, "reason": "snooping"}).status_code == 404  # cannot report what you cannot see
    assert b.post("/api/v1/reports", {"reason": "abusive"}).status_code == 400
