from datetime import timedelta

from app.extensions import db
from app.models import AuthSession, User
from app.utils import utcnow
from tests.conftest import PASSWORD


def test_register_login_me_logout(client):
    r = client.post("/api/v1/auth/register", json={"username": "Alice_1", "password": PASSWORD, "display_name": "Alice"})
    assert r.status_code == 201
    assert "password" not in r.get_data(as_text=True)
    token = r.get_json()["token"]
    h = {"Authorization": f"Bearer {token}"}
    assert client.get("/api/v1/auth/me", headers=h).get_json()["user"]["username"] == "Alice_1"

    login = client.post("/api/v1/auth/login", json={"username": "alice_1", "password": PASSWORD})  # case-insensitive
    assert login.status_code == 200
    token2 = login.get_json()["token"]
    assert token2 != token

    assert client.post("/api/v1/auth/logout", headers=h).status_code == 204
    assert client.get("/api/v1/auth/me", headers=h).status_code == 401  # revoked
    assert client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token2}"}).status_code == 200


def test_password_stored_hashed_only(app, client):
    client.post("/api/v1/auth/register", json={"username": "bob_b", "password": PASSWORD})
    with app.app_context():
        user = db.session.query(User).filter_by(username_normalized="bob_b").one()
        assert user.password_hash.startswith("$argon2id$")
        assert PASSWORD not in user.password_hash


def test_token_is_stored_only_as_hash(app, client):
    token = client.post("/api/v1/auth/register", json={"username": "carol_c", "password": PASSWORD}).get_json()["token"]
    with app.app_context():
        assert all(s.token_hash != token for s in db.session.query(AuthSession))


def test_duplicate_username_case_insensitive(client):
    assert client.post("/api/v1/auth/register", json={"username": "Dave_d", "password": PASSWORD}).status_code == 201
    r = client.post("/api/v1/auth/register", json={"username": "dave_D", "password": PASSWORD})
    assert r.status_code == 409 and r.get_json()["error"]["code"] == "username_taken"


def test_register_validation(client):
    assert client.post("/api/v1/auth/register", json={"username": "ab", "password": PASSWORD}).status_code == 400
    assert client.post("/api/v1/auth/register", json={"username": "bad name!", "password": PASSWORD}).status_code == 400
    assert client.post("/api/v1/auth/register", json={"username": "okname", "password": "short"}).status_code == 400
    assert client.post("/api/v1/auth/register", data="notjson", content_type="text/plain").status_code == 400


def test_login_errors_are_generic(client, make_user):
    make_user("erin_e")
    wrong_pw = client.post("/api/v1/auth/login", json={"username": "erin_e", "password": "wrong-password-1"})
    no_user = client.post("/api/v1/auth/login", json={"username": "nobody_x", "password": "wrong-password-1"})
    assert wrong_pw.status_code == no_user.status_code == 401
    assert wrong_pw.get_json() == no_user.get_json()


def test_missing_or_garbage_token_rejected(client):
    assert client.get("/api/v1/auth/me").status_code == 401
    assert client.get("/api/v1/auth/me", headers={"Authorization": "Bearer nope"}).status_code == 401
    assert client.get("/api/v1/auth/me", headers={"Authorization": "Basic abc"}).status_code == 401


def test_session_idle_expiry(app, make_user):
    a = make_user()
    with app.app_context():
        s = db.session.query(AuthSession).one()
        s.expires_at = utcnow() - timedelta(seconds=1)
        db.session.commit()
    assert a.get("/api/v1/auth/me").status_code == 401


def test_session_absolute_expiry(app, make_user):
    a = make_user()
    with app.app_context():
        s = db.session.query(AuthSession).one()
        s.created_at = utcnow() - timedelta(days=365)
        db.session.commit()
    assert a.get("/api/v1/auth/me").status_code == 401


def test_profile_update(make_user):
    a = make_user()
    r = a.patch("/api/v1/auth/me", {"display_name": "New Name", "email": "a@example.com"})
    assert r.status_code == 200
    u = r.get_json()["user"]
    assert u["display_name"] == "New Name" and u["email"] == "a@example.com" and u["email_verified"] is False


def test_health(client):
    r = client.get("/api/v1/health")
    assert r.status_code == 200 and r.get_json()["e2e_enabled"] is False


def test_devices_list_and_revoke_kills_session(client, make_user):
    a = make_user("frank_f")
    second = client.post("/api/v1/auth/login", json={"username": "frank_f", "password": PASSWORD, "device_name": "Tablet"}).get_json()
    devices = a.get("/api/v1/devices").get_json()["devices"]
    assert len(devices) == 2 and sum(d["current"] for d in devices) == 1
    r = a.post(f"/api/v1/devices/{second['device_id']}/revoke")
    assert r.status_code == 200 and r.get_json()["device"]["status"] == "revoked"
    assert client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {second['token']}"}).status_code == 401
    assert a.get("/api/v1/auth/me").status_code == 200


def test_cannot_revoke_someone_elses_device(make_user):
    a, b = make_user(), make_user()
    dev = b.get("/api/v1/devices").get_json()["devices"][0]["id"]
    assert a.post(f"/api/v1/devices/{dev}/revoke").status_code == 404
    assert b.get("/api/v1/auth/me").status_code == 200


def test_settings_roundtrip(make_user):
    a = make_user()
    assert a.get("/api/v1/settings").get_json()["settings"]["discoverable"] is True
    assert a.patch("/api/v1/settings", {"discoverable": False}).status_code == 200
    assert a.patch("/api/v1/settings", {"discoverable": "yes"}).status_code == 400
