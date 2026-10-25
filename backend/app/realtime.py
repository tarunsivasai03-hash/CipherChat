"""Server -> client event fan-out. Services call this after a successful commit."""
import logging

from app.extensions import socketio

log = logging.getLogger(__name__)


def user_room(user_id: int) -> str:
    return f"user:{user_id}"


def conversation_room(conversation_id: int) -> str:
    return f"conv:{conversation_id}"


def emit_to_users(user_ids, event: str, data: dict) -> None:
    for uid in set(user_ids):
        try:
            socketio.emit(event, data, to=user_room(uid))
        except Exception:  # never let a realtime failure break a committed request
            log.warning("socket emit failed for event %s", event)
