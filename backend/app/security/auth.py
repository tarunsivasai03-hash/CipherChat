"""Bearer-token authentication shared by HTTP routes and Socket.IO."""
import hashlib
import secrets
from datetime import timedelta
from functools import wraps

from flask import current_app, g, request

from app.extensions import db
from app.models import AuthSession, Device, User
from app.utils import ApiError, utcnow

_LAST_USED_GRANULARITY = timedelta(seconds=60)


def new_token() -> tuple[str, str]:
    """Return (token, token_hash). The token has 256 bits of entropy; only the hash is stored."""
    token = secrets.token_urlsafe(32)
    return token, hash_token(token)


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def authenticate_token(token: str | None) -> tuple[User, AuthSession] | None:
    """Resolve a bearer token to (user, session), or None if invalid/expired/revoked."""
    if not token or len(token) > 200:
        return None
    session = db.session.query(AuthSession).filter_by(token_hash=hash_token(token)).one_or_none()
    return authenticate_session(session) if session is not None else None


def authenticate_session(session: AuthSession | None) -> tuple[User, AuthSession] | None:
    """Validate a session row (revocation, expiry, user and device state) and slide its idle expiry."""
    if session is None or session.revoked_at is not None:
        return None
    now = utcnow()
    cfg = current_app.config
    absolute_end = session.created_at + timedelta(seconds=cfg["SESSION_ABSOLUTE_TTL_SECONDS"])
    if session.expires_at <= now or absolute_end <= now:
        return None
    user = db.session.get(User, session.user_id)
    device = db.session.get(Device, session.device_id)
    if user is None or user.status != "active" or device is None or device.status != "active":
        return None
    if now - session.last_used_at > _LAST_USED_GRANULARITY:
        session.last_used_at = now
        session.expires_at = min(now + timedelta(seconds=cfg["SESSION_IDLE_TTL_SECONDS"]), absolute_end)
        device.last_seen_at = now
        db.session.commit()
    return user, session


def _bearer_token() -> str | None:
    header = request.headers.get("Authorization", "")
    scheme, _, value = header.partition(" ")
    if scheme.lower() != "bearer" or not value:
        return None
    return value.strip()


def login_required(view):
    @wraps(view)
    def wrapper(*args, **kwargs):
        result = authenticate_token(_bearer_token())
        if result is None:
            raise ApiError(401, "unauthorized", "Authentication required.")
        g.user, g.session = result
        return view(*args, **kwargs)

    return wrapper
