"""Configuration, built from environment variables at app-creation time."""
import os

_BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

_PLACEHOLDER_SECRETS = {"", "change-me", "dev", "secret", "changeme"}


def _bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    return int(raw) if raw not in (None, "") else default


def _list(name: str) -> list[str]:
    raw = os.environ.get(name, "")
    return [item.strip() for item in raw.split(",") if item.strip()]


def _default_sqlite_uri() -> str:
    instance_dir = os.path.join(_BASE_DIR, "instance")
    os.makedirs(instance_dir, exist_ok=True)
    return "sqlite:///" + os.path.join(instance_dir, "cipherchat.db").replace("\\", "/")


def build_config(env_name: str | None = None) -> dict:
    env_name = (env_name or os.environ.get("APP_ENV") or "development").lower()
    cfg = {
        "APP_ENV": env_name,
        "SECRET_KEY": os.environ.get("SECRET_KEY", ""),
        "SQLALCHEMY_DATABASE_URI": os.environ.get("DATABASE_URL") or _default_sqlite_uri(),
        "SQLALCHEMY_TRACK_MODIFICATIONS": False,
        "MAX_CONTENT_LENGTH": 64 * 1024,
        # Auth sessions: opaque random tokens, only a SHA-256 hash is stored server-side.
        "SESSION_IDLE_TTL_SECONDS": _int("SESSION_IDLE_TTL_SECONDS", 14 * 24 * 3600),
        "SESSION_ABSOLUTE_TTL_SECONDS": _int("SESSION_ABSOLUTE_TTL_SECONDS", 90 * 24 * 3600),
        # Rate limits (Flask-Limiter syntax).
        "RATELIMIT_ENABLED": _bool("RATELIMIT_ENABLED", True),
        "RATELIMIT_STORAGE_URI": os.environ.get("RATELIMIT_STORAGE_URI", "memory://"),
        "RATELIMIT_DEFAULT": os.environ.get("RATELIMIT_DEFAULT", "300 per minute"),
        "RATELIMIT_HEADERS_ENABLED": True,
        "LOGIN_RATE_LIMIT": os.environ.get("LOGIN_RATE_LIMIT", "10 per minute"),
        "REGISTER_RATE_LIMIT": os.environ.get("REGISTER_RATE_LIMIT", "5 per minute"),
        "SEARCH_RATE_LIMIT": os.environ.get("SEARCH_RATE_LIMIT", "30 per minute"),
        "CONTACT_REQUEST_RATE_LIMIT": os.environ.get("CONTACT_REQUEST_RATE_LIMIT", "20 per hour"),
        "SOCKET_SEND_LIMIT": _int("SOCKET_SEND_LIMIT", 60),  # events per window
        "SOCKET_SEND_WINDOW_SECONDS": _int("SOCKET_SEND_WINDOW_SECONDS", 60),
        "SOCKET_TYPING_LIMIT": _int("SOCKET_TYPING_LIMIT", 60),
        # Socket.IO
        "SOCKETIO_CORS_ORIGINS": _list("SOCKETIO_CORS_ORIGINS"),
        "SOCKETIO_ASYNC_MODE": os.environ.get("SOCKETIO_ASYNC_MODE", "threading"),
        # Transport security
        "REQUIRE_HTTPS": _bool("REQUIRE_HTTPS", env_name == "production"),
        "TRUSTED_PROXY_COUNT": _int("TRUSTED_PROXY_COUNT", 0),
        "LOG_LEVEL": os.environ.get("LOG_LEVEL", "INFO"),
        # The server never accepts E2E payloads until a reviewed protocol is integrated
        # and verified on both sides. This is intentionally not configurable.
        "E2E_ENABLED": False,
    }
    if env_name == "testing":
        cfg["RATELIMIT_ENABLED"] = False
    return cfg


def validate_config(cfg: dict) -> None:
    """Fail fast on unsafe production configuration."""
    if cfg["APP_ENV"] != "production":
        return  # development/testing: create_app generates an ephemeral SECRET_KEY if unset
    secret = cfg["SECRET_KEY"]
    if secret.lower() in _PLACEHOLDER_SECRETS or len(secret) < 32:
        raise RuntimeError("SECRET_KEY must be set to a random value of at least 32 characters in production.")
    if cfg["SQLALCHEMY_DATABASE_URI"].startswith("sqlite") and not os.environ.get("DATABASE_URL"):
        raise RuntimeError("Set DATABASE_URL explicitly in production (point it at a persistent volume).")
    if not cfg["REQUIRE_HTTPS"]:
        raise RuntimeError("REQUIRE_HTTPS must be enabled in production.")
    if "*" in cfg["SOCKETIO_CORS_ORIGINS"]:
        raise RuntimeError("SOCKETIO_CORS_ORIGINS must not contain '*' in production.")
