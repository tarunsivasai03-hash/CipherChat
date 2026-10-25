from flask import Blueprint, g, jsonify

from app.extensions import limiter
from app.routes.ratelimit import cfg_limit, username_key
from app.security.auth import login_required
from app.services import auth_service
from app.utils import ApiError, get_str, json_body

bp = Blueprint("auth", __name__, url_prefix="/api/v1/auth")


@bp.post("/register")
@limiter.limit(cfg_limit("REGISTER_RATE_LIMIT"))
def register():
    data = json_body()
    user = auth_service.register(
        username=get_str(data, "username", max_len=64),
        password=get_str(data, "password", max_len=256),
        display_name=get_str(data, "display_name", required=False, max_len=64),
        email=get_str(data, "email", required=False, max_len=254),
    )
    token, session, device = auth_service.start_session(
        user,
        get_str(data, "device_name", required=False, max_len=64) or "Android device",
        get_str(data, "platform", required=False, max_len=32) or "android",
    )
    return jsonify({"token": token, "device_id": device.id, "user": user.self_dict()}), 201


@bp.post("/login")
@limiter.limit(cfg_limit("LOGIN_RATE_LIMIT"))
@limiter.limit(cfg_limit("LOGIN_RATE_LIMIT"), key_func=username_key)
def login():
    data = json_body()
    user, token, session, device = auth_service.login(
        get_str(data, "username", max_len=64),
        get_str(data, "password", max_len=256),
        get_str(data, "device_name", required=False, max_len=64) or "Android device",
        get_str(data, "platform", required=False, max_len=32) or "android",
    )
    return jsonify({"token": token, "device_id": device.id, "user": user.self_dict()})


@bp.post("/logout")
@login_required
def logout():
    auth_service.revoke_session(g.session)
    return "", 204


@bp.get("/me")
@login_required
def me():
    return jsonify({"user": g.user.self_dict(), "device_id": g.session.device_id})


@bp.patch("/me")
@login_required
def update_me():
    data = json_body()
    if not data:
        raise ApiError(400, "invalid_json", "Nothing to update.")
    user = auth_service.update_profile(
        g.user,
        get_str(data, "display_name", required=False, max_len=64),
        get_str(data, "email", required=False, max_len=254),
        email_given="email" in data,
    )
    return jsonify({"user": user.self_dict()})
