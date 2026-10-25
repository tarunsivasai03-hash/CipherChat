from app import create_app
from app.extensions import db, limiter
from tests.conftest import PASSWORD


def _limited_app(tmp_path):
    app = create_app(
        {
            "SQLALCHEMY_DATABASE_URI": f"sqlite:///{(tmp_path / 'rl.db').as_posix()}",
            "RATELIMIT_ENABLED": True,
            "LOGIN_RATE_LIMIT": "3 per minute",
            "REGISTER_RATE_LIMIT": "2 per minute",
            "SEARCH_RATE_LIMIT": "2 per minute",
        },
        env="testing",
    )
    with app.app_context():
        db.create_all()
    limiter.reset()
    return app


def test_login_is_rate_limited_per_ip_and_per_username(tmp_path):
    app = _limited_app(tmp_path)
    c = app.test_client()
    c.post("/api/v1/auth/register", json={"username": "victim_v", "password": PASSWORD})
    codes = [c.post("/api/v1/auth/login", json={"username": "victim_v", "password": "bad-password-1"}).status_code for _ in range(5)]
    assert codes[:3] == [401, 401, 401] and codes[3:] == [429, 429]
    # Different source IP, same username: still throttled by the per-username limiter.
    other = c.post(
        "/api/v1/auth/login",
        json={"username": "victim_v", "password": PASSWORD},
        environ_overrides={"REMOTE_ADDR": "203.0.113.9"},
    )
    assert other.status_code == 429 and other.get_json()["error"]["code"] == "rate_limited"
    with app.app_context():
        db.session.remove()
        db.engine.dispose()
    limiter.reset()


def test_register_and_search_are_rate_limited(tmp_path):
    app = _limited_app(tmp_path)
    c = app.test_client()
    results = [
        c.post("/api/v1/auth/register", json={"username": f"spam_user{i}", "password": PASSWORD}).status_code for i in range(3)
    ]
    assert results == [201, 201, 429]
    token = c.post(
        "/api/v1/auth/register",
        json={"username": "other_one", "password": PASSWORD},
        environ_overrides={"REMOTE_ADDR": "198.51.100.5"},
    ).get_json()["token"]
    h = {"Authorization": f"Bearer {token}"}
    codes = [c.get("/api/v1/users/search?q=spam", headers=h).status_code for _ in range(3)]
    assert codes == [200, 200, 429]
    with app.app_context():
        db.session.remove()
        db.engine.dispose()
    limiter.reset()
