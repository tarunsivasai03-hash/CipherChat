from app.extensions import db
from app.models import AuthSession, Block, Contact, ContactRequest, Conversation, ConversationMember, Device, Message, User
from tests.conftest import PASSWORD


def test_delete_requires_password(make_user):
    a = make_user()
    assert a.delete("/api/v1/account", {"password": "wrong-password-1"}).status_code == 403
    assert a.get("/api/v1/auth/me").status_code == 200


def test_delete_removes_all_user_data(app, client, make_user, direct, send, befriend):
    a, b, c = make_user("del_alice"), make_user("del_bob"), make_user("del_carol")
    cid = direct(a, b)
    befriend(a, c)
    send(a, cid, "from a")
    send(b, cid, "from b")
    a.post("/api/v1/blocks", {"user_id": c.id})

    assert a.delete("/api/v1/account", {"password": PASSWORD}).status_code == 204
    assert client.post("/api/v1/auth/login", json={"username": "del_alice", "password": PASSWORD}).status_code == 401
    assert a.get("/api/v1/auth/me").status_code == 401

    with app.app_context():
        assert db.session.query(User).filter_by(username_normalized="del_alice").count() == 0
        assert db.session.query(Message).count() == 0
        assert db.session.query(Conversation).count() == 0  # the direct thread is gone
        assert db.session.query(Contact).count() == 0
        assert db.session.query(ContactRequest).count() == 0
        assert db.session.query(Block).count() == 0
        assert db.session.query(AuthSession).filter_by(user_id=a.id).count() == 0
        assert db.session.query(Device).filter_by(user_id=a.id).count() == 0
        assert db.session.query(User).count() == 2
    assert b.get("/api/v1/conversations").get_json()["conversations"] == []


def test_delete_keeps_groups_and_promotes_admin(app, make_user, befriend, send):
    a, b = make_user(), make_user()
    befriend(a, b)
    gid = a.post("/api/v1/conversations", {"type": "group", "name": "G", "member_ids": [b.id]}).get_json()["conversation"]["id"]
    send(a, gid, "gone with a")
    send(b, gid, "stays")
    assert a.delete("/api/v1/account", {"password": PASSWORD}).status_code == 204
    msgs = b.get(f"/api/v1/conversations/{gid}/messages").get_json()["messages"]
    assert [m["payload"] for m in msgs] == ["stays"]
    with app.app_context():
        assert db.session.get(ConversationMember, (gid, b.id)).role == "admin"
