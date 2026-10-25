"""Cross-user access, forged identifiers, leakage, and hardening checks."""
import logging

from app.extensions import db
from app.models import Message, User


def test_outsider_cannot_read_or_write_conversation(make_user, direct, send):
    a, b, evil = make_user(), make_user(), make_user()
    cid = direct(a, b)
    mid = send(a, cid, "private").get_json()["message"]["id"]
    assert evil.get(f"/api/v1/conversations/{cid}").status_code == 404
    assert evil.get(f"/api/v1/conversations/{cid}/messages").status_code == 404
    assert send(evil, cid, "intrude").status_code == 404
    assert evil.post(f"/api/v1/conversations/{cid}/seen", {"message_id": 1}).status_code == 404
    assert evil.patch(f"/api/v1/conversations/{cid}", {"disappear_after_seconds": 10}).status_code == 404
    assert evil.delete(f"/api/v1/messages/{mid}").status_code == 404
    assert evil.put(f"/api/v1/messages/{mid}/reaction", {"emoji": "👍"}).status_code == 404
    # nonexistent and forbidden look identical
    assert evil.get("/api/v1/conversations/999999").status_code == 404


def test_unauthenticated_access_denied_everywhere(client):
    for method, url in [
        ("get", "/api/v1/conversations"),
        ("get", "/api/v1/conversations/1/messages"),
        ("post", "/api/v1/conversations/1/messages"),
        ("get", "/api/v1/contacts"),
        ("get", "/api/v1/devices"),
        ("delete", "/api/v1/account"),
        ("get", "/api/v1/users/search?q=abc"),
        ("get", "/api/v1/settings"),
        ("delete", "/api/v1/messages/1"),
    ]:
        assert getattr(client, method)(url).status_code == 401, (method, url)


def test_forged_sender_id_in_rest_body_ignored(make_user, direct):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    r = a.post(
        f"/api/v1/conversations/{cid}/messages",
        {"kind": "plain", "payload": "x", "client_msg_id": "forge-000001", "sender_id": b.id, "conversation_id": 99},
    )
    assert r.get_json()["message"]["sender_id"] == a.id and r.get_json()["message"]["conversation_id"] == cid


def test_cannot_inject_member_via_direct_creation_or_group_ids(make_user, befriend):
    a, b, stranger = make_user(), make_user(), make_user()
    befriend(a, b)
    assert a.post("/api/v1/conversations", {"type": "direct", "user_id": stranger.id}).status_code == 403
    assert a.post("/api/v1/conversations", {"type": "group", "name": "G", "member_ids": [stranger.id]}).status_code == 403
    assert a.post("/api/v1/conversations", {"type": "group", "name": "G", "member_ids": ["1"]}).status_code == 400


def test_reply_to_other_conversation_rejected(make_user, direct, send):
    a, b, c = make_user(), make_user(), make_user()
    c1, c2 = direct(a, b), direct(a, c)
    foreign = send(a, c2, "other thread").get_json()["message"]["id"]
    assert send(a, c1, "sneaky", reply_to_id=foreign).status_code == 400


def test_user_payloads_never_expose_secrets(make_user):
    a = make_user()
    b = make_user()
    text = a.get(f"/api/v1/users/search?q={b.username[:6]}").get_data(as_text=True) + a.get("/api/v1/auth/me").get_data(as_text=True)
    assert "password" not in text.lower() and "argon2" not in text.lower() and "token_hash" not in text.lower()


def test_search_does_not_leak_email(make_user):
    a, b = make_user("leaky_aaa"), make_user("leaky_bbb")
    b.patch("/api/v1/auth/me", {"email": "secret@example.com"})
    body = a.get("/api/v1/users/search?q=leaky").get_data(as_text=True)
    assert "secret@example.com" not in body


def test_security_headers_and_json_errors(client):
    r = client.get("/api/v1/nope")
    assert r.status_code == 404 and r.get_json()["error"]["code"] == "not_found"
    assert r.headers["X-Content-Type-Options"] == "nosniff"
    assert r.headers["Cache-Control"] == "no-store"
    assert "frame-ancestors 'none'" in r.headers["Content-Security-Policy"]


def test_server_errors_do_not_leak_details(app, client, monkeypatch):
    def boom(*a, **k):
        raise RuntimeError("secret-internal-detail")

    monkeypatch.setattr("app.routes.health.db.session.execute", boom)
    app.config["PROPAGATE_EXCEPTIONS"] = False
    r = client.get("/api/v1/health")
    assert "secret-internal-detail" not in r.get_data(as_text=True)


def test_plaintext_not_logged(app, make_user, direct, send, caplog):
    a, b = make_user(), make_user()
    cid = direct(a, b)
    with caplog.at_level(logging.DEBUG):
        send(a, cid, "SUPER-SECRET-MARKER-123")
        a.get(f"/api/v1/conversations/{cid}/messages")
    assert "SUPER-SECRET-MARKER-123" not in caplog.text
    assert a.token not in caplog.text


def test_no_private_key_material_columns(app):
    """The schema has nowhere to put a private key; the only key column is a (currently unused) public one."""
    from sqlalchemy import inspect

    with app.app_context():
        cols = {t: [c["name"] for c in inspect(db.engine).get_columns(t)] for t in inspect(db.engine).get_table_names()}
    flat = [c for names in cols.values() for c in names]
    assert not any("private" in c or "secret" in c for c in flat)
    assert {c for c in flat if "key" in c} == {"identity_public_key", "direct_key"}


def test_https_enforced_when_required(tmp_path):
    from app import create_app

    app = create_app(
        {"SQLALCHEMY_DATABASE_URI": f"sqlite:///{(tmp_path / 'h.db').as_posix()}", "REQUIRE_HTTPS": True},
        env="testing",
    )
    with app.app_context():
        db.create_all()
    c = app.test_client()
    assert c.get("/api/v1/auth/me").status_code == 400
    r = c.get("/api/v1/auth/me", base_url="https://localhost")
    assert r.status_code == 401 and "max-age" in r.headers["Strict-Transport-Security"]
    with app.app_context():
        db.session.remove()
        db.engine.dispose()


def test_production_config_validation(monkeypatch):
    import pytest

    from app.config import build_config, validate_config

    monkeypatch.delenv("SECRET_KEY", raising=False)
    with pytest.raises(RuntimeError):
        validate_config(build_config("production"))
    monkeypatch.setenv("SECRET_KEY", "x" * 40)
    monkeypatch.setenv("DATABASE_URL", "sqlite:////tmp/x.db")
    validate_config(build_config("production"))
    monkeypatch.setenv("SOCKETIO_CORS_ORIGINS", "*")
    with pytest.raises(RuntimeError):
        validate_config(build_config("production"))
