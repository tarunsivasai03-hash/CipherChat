from flask import Blueprint, g, jsonify

from app.extensions import db
from app.models import Device
from app.security.auth import login_required
from app.services import auth_service
from app.utils import ApiError, json_body, get_str

bp = Blueprint("account", __name__, url_prefix="/api/v1")


@bp.get("/devices")
@login_required
def list_devices():
    devices = db.session.query(Device).filter_by(user_id=g.user.id).order_by(Device.id.desc()).all()
    return jsonify({"devices": [d.to_dict(g.session.device_id) for d in devices]})


@bp.post("/devices/<int:device_id>/revoke")
@login_required
def revoke_device(device_id: int):
    device = auth_service.revoke_device(g.user, device_id)
    return jsonify({"device": device.to_dict(g.session.device_id)})


@bp.get("/settings")
@login_required
def get_settings():
    return jsonify({"settings": {"discoverable": g.user.discoverable}})


@bp.patch("/settings")
@login_required
def patch_settings():
    data = json_body()
    if "discoverable" not in data or not isinstance(data["discoverable"], bool):
        raise ApiError(400, "invalid_field", "'discoverable' must be a boolean.")
    g.user.discoverable = data["discoverable"]
    db.session.commit()
    return jsonify({"settings": {"discoverable": g.user.discoverable}})


@bp.delete("/account")
@login_required
def delete_account():
    password = get_str(json_body(), "password", max_len=256)
    auth_service.delete_account(g.user, password)
    return "", 204
