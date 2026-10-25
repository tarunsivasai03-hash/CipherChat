"""CipherChat backend application factory."""
import logging
import secrets
import sqlite3

import click
from dotenv import load_dotenv
from flask import Flask, jsonify, request
from sqlalchemy import event
from sqlalchemy.engine import Engine
from werkzeug.exceptions import HTTPException
from werkzeug.middleware.proxy_fix import ProxyFix

from app.config import build_config, validate_config
from app.extensions import db, limiter, migrate, socketio
from app.utils import ApiError


@event.listens_for(Engine, "connect")
def _sqlite_pragmas(dbapi_connection, _record):
    """SQLite ignores foreign keys unless asked; also use WAL for better read/write concurrency."""
    if isinstance(dbapi_connection, sqlite3.Connection):
        cur = dbapi_connection.cursor()
        cur.execute("PRAGMA foreign_keys=ON")
        cur.execute("PRAGMA journal_mode=WAL")
        cur.close()


def create_app(config_overrides: dict | None = None, env: str | None = None) -> Flask:
    load_dotenv()
    cfg = build_config(env)
    cfg.update(config_overrides or {})
    validate_config(cfg)
    if not cfg["SECRET_KEY"]:
        cfg["SECRET_KEY"] = secrets.token_hex(32)  # ephemeral; development/testing only
    # hide_parameters keeps bound values (e.g. message payloads) out of SQLAlchemy error text and logs.
    cfg.setdefault("SQLALCHEMY_ENGINE_OPTIONS", {"hide_parameters": True, "pool_pre_ping": True})

    app = Flask(__name__)
    app.config.update(cfg)
    logging.basicConfig(
        level=getattr(logging, str(cfg["LOG_LEVEL"]).upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    if cfg["TRUSTED_PROXY_COUNT"]:
        n = cfg["TRUSTED_PROXY_COUNT"]
        app.wsgi_app = ProxyFix(app.wsgi_app, x_for=n, x_proto=n, x_host=n)

    # Imported before socketio.init_app so handlers are queued on the SocketIO object and applied to
    # every app instance (importing afterwards would attach them only to the first server).
    from app import models  # noqa: F401  (register tables with metadata)
    from app.sockets import events  # noqa: F401  (register socket handlers)

    db.init_app(app)
    migrate.init_app(app, db, render_as_batch=True)
    limiter.init_app(app)
    socketio.init_app(
        app,
        cors_allowed_origins=cfg["SOCKETIO_CORS_ORIGINS"] or None,  # None = same-origin only
        async_mode=cfg["SOCKETIO_ASYNC_MODE"],
        logger=False,
        engineio_logger=False,
        max_http_buffer_size=64 * 1024,
    )

    from app.routes import register_blueprints

    register_blueprints(app)
    _register_hooks(app)
    _register_cli(app)
    return app


def _error(status: int, code: str, message: str):
    return jsonify({"error": {"code": code, "message": message}}), status


def _register_hooks(app: Flask) -> None:
    @app.before_request
    def _require_https():
        if app.config["REQUIRE_HTTPS"] and not request.is_secure and request.path != "/api/v1/health":
            return _error(400, "https_required", "HTTPS is required.")

    @app.after_request
    def _security_headers(resp):
        resp.headers["X-Content-Type-Options"] = "nosniff"
        resp.headers["X-Frame-Options"] = "DENY"
        resp.headers["Referrer-Policy"] = "no-referrer"
        resp.headers["Content-Security-Policy"] = "default-src 'none'; frame-ancestors 'none'"
        resp.headers["Cache-Control"] = "no-store"
        if app.config["REQUIRE_HTTPS"]:
            resp.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
        return resp

    @app.teardown_request
    def _cleanup(exc):
        if exc is not None:
            db.session.rollback()

    @app.errorhandler(ApiError)
    def _api_error(err: ApiError):
        db.session.rollback()
        return _error(err.status, err.code, err.message)

    @app.errorhandler(HTTPException)
    def _http_error(err: HTTPException):
        code = {429: "rate_limited", 404: "not_found", 405: "method_not_allowed", 413: "payload_too_large"}.get(
            err.code, "http_error"
        )
        resp = _error(err.code or 500, code, err.name)
        for key, value in (err.get_response().headers if err.code == 429 else []):
            if key.lower() == "retry-after":
                resp[0].headers[key] = value
        return resp

    @app.errorhandler(Exception)
    def _unhandled(err: Exception):
        db.session.rollback()
        app.logger.error("unhandled %s on %s %s", type(err).__name__, request.method, request.path)
        return _error(500, "server_error", "Something went wrong.")


def _register_cli(app: Flask) -> None:
    @app.cli.command("purge-expired")
    def purge_expired():
        """Delete disappearing messages whose timer has elapsed."""
        from app.services.message_service import purge_expired as run

        click.echo(f"Deleted {run()} expired messages")
