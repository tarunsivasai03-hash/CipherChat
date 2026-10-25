"""Socket.IO events.

Every sensitive event re-validates the session AND conversation membership server-side.
Client-supplied sender ids, room names and membership claims are never used.
"""
import logging
from functools import wraps

from flask import current_app, request
from flask_socketio import ConnectionRefusedError, emit, join_room, leave_room

from app.extensions import db, socketio
from app.models import AuthSession
from app.realtime import conversation_room
from app.security.auth import authenticate_session, authenticate_token
from app.services import conversation_service, message_service
from app.sockets import registry
from app.utils import ApiError

log = logging.getLogger(__name__)


@socketio.on("connect")
def on_connect(auth):
    token = auth.get("token") if isinstance(auth, dict) else None
    result = authenticate_token(token if isinstance(token, str) else None)
    if result is None:
        raise ConnectionRefusedError("unauthorized")
    user, session = result
    registry.register(request.sid, user.id, session.id)
    from app.realtime import user_room

    join_room(user_room(user.id))


@socketio.on("disconnect")
def on_disconnect(*_):
    registry.unregister(request.sid)


def _authenticated():
    entry = registry.get(request.sid)
    if entry is None:
        return None
    session = db.session.get(AuthSession, entry[1])
    return authenticate_session(session)


def handler(event: str, *, bucket: str | None = None, limit_key: str | None = None, window_key: str | None = None):
    """Register an event handler with auth, error mapping, and optional per-user throttling."""

    def decorator(fn):
        @socketio.on(event)
        @wraps(fn)
        def wrapper(data=None):
            try:
                result = _authenticated()
                if result is None:
                    entry = registry.get(request.sid)
                    if entry is not None:
                        registry.disconnect_session(entry[1])
                    return {"ok": False, "error": "unauthorized"}
                user, _ = result
                if not isinstance(data, dict):
                    return {"ok": False, "error": "invalid_payload"}
                if bucket:
                    cfg = current_app.config
                    limit = cfg[limit_key]
                    window = cfg[window_key] if window_key else 60
                    if not registry.allow(user.id, bucket, limit, window):
                        return {"ok": False, "error": "rate_limited"}
                return fn(user, data)
            except ApiError as err:
                db.session.rollback()
                return {"ok": False, "error": err.code}
            except Exception:
                db.session.rollback()
                log.exception("socket handler %s failed", event)
                return {"ok": False, "error": "server_error"}
            finally:
                db.session.remove()

        return wrapper

    return decorator


def _conv_id(data: dict) -> int:
    value = data.get("conversation_id")
    if isinstance(value, bool) or not isinstance(value, int):
        raise ApiError(400, "invalid_field", "conversation_id must be an integer.")
    return value


@handler("join_conversation")
def join_conversation(user, data):
    cid = _conv_id(data)
    conversation_service.require_member(user.id, cid)  # raises not_found for non-members
    join_room(conversation_room(cid))
    return {"ok": True}


@handler("leave_conversation")
def leave_conversation(user, data):
    leave_room(conversation_room(_conv_id(data)))
    return {"ok": True}


@handler("send_message", bucket="send", limit_key="SOCKET_SEND_LIMIT", window_key="SOCKET_SEND_WINDOW_SECONDS")
def send_message(user, data):
    message, created = message_service.send_message(
        user,
        _conv_id(data),
        kind=data.get("kind", "plain"),
        payload=data.get("payload"),
        client_msg_id=data.get("client_msg_id"),
        meta=data.get("meta"),
        reply_to_id=data.get("reply_to_id"),
    )
    # "accepted" means durably stored by the server. It says nothing about delivery or reading.
    return {"ok": True, "status": "accepted", "created": created, "message": message_service.serialize_many([message])[0]}


@handler("message_delivered")
def message_delivered(user, data):
    mid = data.get("message_id")
    if isinstance(mid, bool) or not isinstance(mid, int):
        raise ApiError(400, "invalid_field", "message_id must be an integer.")
    return {"ok": True, "recorded": message_service.record_delivery(user, mid)}


@handler("typing", bucket="typing", limit_key="SOCKET_TYPING_LIMIT")
def typing(user, data):
    cid = _conv_id(data)
    conversation_service.require_member(user.id, cid)
    emit(
        "typing",
        {"conversation_id": cid, "user_id": user.id, "is_typing": bool(data.get("is_typing", True))},
        to=conversation_room(cid),
        include_self=False,
    )
    return {"ok": True}
