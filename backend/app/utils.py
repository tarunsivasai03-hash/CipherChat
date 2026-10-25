"""Small shared helpers: time, errors, request parsing."""
from datetime import datetime, timezone

from flask import request


def utcnow() -> datetime:
    """Naive UTC timestamp (SQLite drops tzinfo, so everything is stored naive-UTC)."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


def iso(dt: datetime | None) -> str | None:
    return None if dt is None else dt.replace(microsecond=0).isoformat() + "Z"


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str | None = None):
        super().__init__(code)
        self.status = status
        self.code = code
        self.message = message or code.replace("_", " ")


def json_body() -> dict:
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        raise ApiError(400, "invalid_json", "Request body must be a JSON object.")
    return data


def get_str(data: dict, key: str, *, required: bool = True, max_len: int = 256, min_len: int = 0) -> str | None:
    value = data.get(key)
    if value is None:
        if required:
            raise ApiError(400, "invalid_field", f"'{key}' is required.")
        return None
    if not isinstance(value, str):
        raise ApiError(400, "invalid_field", f"'{key}' must be a string.")
    if len(value) > max_len or len(value) < min_len:
        raise ApiError(400, "invalid_field", f"'{key}' has an invalid length.")
    return value


def get_int(data: dict, key: str, *, required: bool = True) -> int | None:
    value = data.get(key)
    if value is None:
        if required:
            raise ApiError(400, "invalid_field", f"'{key}' is required.")
        return None
    if isinstance(value, bool) or not isinstance(value, int):
        raise ApiError(400, "invalid_field", f"'{key}' must be an integer.")
    return value


def query_int(name: str, default: int, *, minimum: int = 1, maximum: int = 100) -> int:
    raw = request.args.get(name)
    if raw is None or raw == "":
        return default
    try:
        value = int(raw)
    except ValueError:
        raise ApiError(400, "invalid_query", f"'{name}' must be an integer.")
    return max(minimum, min(maximum, value))
