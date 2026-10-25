import itertools

import pytest

from app import create_app
from app.extensions import db, limiter
from app.sockets import registry

_counter = itertools.count(1)
PASSWORD = "correct-horse-battery"


class Actor:
    """A registered user plus helpers for authenticated requests."""

    def __init__(self, client, username, token, user_id):
        self.client, self.username, self.token, self.id = client, username, token, user_id

    @property
    def headers(self):
        return {"Authorization": f"Bearer {self.token}"}

    def get(self, url, **kw):
        return self.client.get(url, headers=self.headers, **kw)

    def post(self, url, json=None, **kw):
        return self.client.post(url, json=json if json is not None else {}, headers=self.headers, **kw)

    def patch(self, url, json=None):
        return self.client.patch(url, json=json or {}, headers=self.headers)

    def put(self, url, json=None):
        return self.client.put(url, json=json or {}, headers=self.headers)

    def delete(self, url, json=None):
        return self.client.delete(url, json=json, headers=self.headers)


@pytest.fixture
def app(tmp_path):
    app = create_app(
        {"SQLALCHEMY_DATABASE_URI": f"sqlite:///{(tmp_path / 'test.db').as_posix()}", "TESTING": True},
        env="testing",
    )
    with app.app_context():
        db.create_all()
    registry.reset()
    yield app
    with app.app_context():
        db.session.remove()
        db.engine.dispose()


@pytest.fixture
def client(app):
    return app.test_client()


@pytest.fixture
def make_user(client):
    def _make(name=None, password=PASSWORD):
        name = name or f"user{next(_counter)}"
        resp = client.post("/api/v1/auth/register", json={"username": name, "password": password})
        assert resp.status_code == 201, resp.get_json()
        body = resp.get_json()
        return Actor(client, name, body["token"], body["user"]["id"])

    return _make


@pytest.fixture
def befriend():
    def _befriend(a: Actor, b: Actor):
        assert a.post("/api/v1/contact-requests", {"user_id": b.id}).status_code == 202
        reqs = b.get("/api/v1/contact-requests").get_json()["incoming"]
        rid = next(r["id"] for r in reqs if r["sender"]["id"] == a.id)
        assert b.post(f"/api/v1/contact-requests/{rid}/accept").status_code == 200

    return _befriend


@pytest.fixture
def direct(befriend):
    def _direct(a: Actor, b: Actor) -> int:
        befriend(a, b)
        resp = a.post("/api/v1/conversations", {"type": "direct", "user_id": b.id})
        assert resp.status_code in (200, 201), resp.get_json()
        return resp.get_json()["conversation"]["id"]

    return _direct


@pytest.fixture
def send():
    n = itertools.count(1)

    def _send(actor: Actor, cid: int, text="hello", **extra):
        body = {"kind": "plain", "payload": text, "client_msg_id": f"cid-{next(n):08d}", **extra}
        return actor.post(f"/api/v1/conversations/{cid}/messages", body)

    return _send


@pytest.fixture(autouse=True)
def _reset_limiter():
    yield
    try:
        limiter.reset()
    except Exception:
        pass
