from flask import Blueprint, jsonify
from sqlalchemy import text

from app.extensions import db

bp = Blueprint("health", __name__)


@bp.get("/api/v1/health")
def health():
    try:
        db.session.execute(text("SELECT 1"))
        db_ok = True
    except Exception:
        db_ok = False
    status = 200 if db_ok else 503
    # e2e_enabled is the honest capability flag clients use to decide whether to offer Private Chat.
    return jsonify({"status": "ok" if db_ok else "degraded", "e2e_enabled": False, "api_version": 1}), status
