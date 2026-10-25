"""In-process socket registry and rate windows.

This is per-process state, which is why the Socket.IO deployment is a single worker
(see docs/DEPLOYMENT.md). Scaling out needs a Socket.IO message queue and a shared registry.
"""
import logging
import threading
import time
from collections import defaultdict, deque

from app.extensions import socketio

log = logging.getLogger(__name__)

_lock = threading.Lock()
_sids: dict[str, tuple[int, int]] = {}  # sid -> (user_id, session_id)
_windows: dict[tuple[int, str], deque] = defaultdict(deque)


def register(sid: str, user_id: int, session_id: int) -> None:
    with _lock:
        _sids[sid] = (user_id, session_id)


def unregister(sid: str) -> None:
    with _lock:
        _sids.pop(sid, None)


def get(sid: str) -> tuple[int, int] | None:
    with _lock:
        return _sids.get(sid)


def reset() -> None:
    with _lock:
        _sids.clear()
        _windows.clear()


def allow(user_id: int, bucket: str, limit: int, window_seconds: int) -> bool:
    """Sliding-window limiter. Returns False when the caller should be throttled."""
    now = time.monotonic()
    with _lock:
        q = _windows[(user_id, bucket)]
        while q and now - q[0] > window_seconds:
            q.popleft()
        if len(q) >= limit:
            return False
        q.append(now)
        return True


def _disconnect(sids: list[str]) -> None:
    for sid in sids:
        try:
            socketio.server.disconnect(sid, namespace="/")
        except Exception:
            log.debug("socket disconnect failed")


def disconnect_session(session_id: int) -> None:
    with _lock:
        sids = [sid for sid, (_, s) in _sids.items() if s == session_id]
    _disconnect(sids)


def disconnect_user(user_id: int) -> None:
    with _lock:
        sids = [sid for sid, (u, _) in _sids.items() if u == user_id]
    _disconnect(sids)


def evict_from_room(user_id: int, room: str) -> None:
    with _lock:
        sids = [sid for sid, (u, _) in _sids.items() if u == user_id]
    for sid in sids:
        try:
            socketio.server.leave_room(sid, room, namespace="/")
        except Exception:
            log.debug("socket leave_room failed")
