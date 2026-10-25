"""Rate-limit key functions and limit providers (limits come from app config)."""
from flask import current_app, request

from app.security.auth import hash_token


def cfg_limit(name: str):
    return lambda: current_app.config[name]


def token_key() -> str:
    """Per-session key for authenticated endpoints (falls back to IP)."""
    header = request.headers.get("Authorization", "")
    _, _, value = header.partition(" ")
    return "tok:" + hash_token(value.strip()) if value.strip() else "ip:" + (request.remote_addr or "?")


def username_key() -> str:
    """Per-username key for credential endpoints, so one account cannot be brute-forced across IPs."""
    data = request.get_json(silent=True)
    name = data.get("username") if isinstance(data, dict) else None
    return "user:" + (name.strip().lower()[:64] if isinstance(name, str) else "-")
