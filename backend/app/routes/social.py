from flask import Blueprint, g, jsonify, request

from app.extensions import db
from app.extensions import limiter
from app.models import Message, Report, User
from app.routes.ratelimit import cfg_limit, token_key
from app.security.auth import login_required
from app.services import contact_service
from app.utils import ApiError, get_int, get_str, json_body

bp = Blueprint("social", __name__, url_prefix="/api/v1")


@bp.get("/users/search")
@login_required
@limiter.limit(cfg_limit("SEARCH_RATE_LIMIT"), key_func=token_key)
def search_users():
    users = contact_service.search_users(g.user, request.args.get("q", ""))
    return jsonify({"users": [u.public_dict() for u in users]})


@bp.get("/contacts")
@login_required
def contacts():
    return jsonify({"contacts": [u.public_dict() for u in contact_service.list_contacts(g.user)]})


@bp.delete("/contacts/<int:user_id>")
@login_required
def remove_contact(user_id: int):
    contact_service.remove_contact(g.user, user_id)
    return "", 204


@bp.get("/contact-requests")
@login_required
def list_requests():
    return jsonify(contact_service.list_requests(g.user))


@bp.post("/contact-requests")
@login_required
@limiter.limit(cfg_limit("CONTACT_REQUEST_RATE_LIMIT"), key_func=token_key)
def create_request():
    data = json_body()
    contact_service.send_request(
        g.user,
        get_int(data, "user_id", required=False),
        get_str(data, "username", required=False, max_len=64),
    )
    # Same response whether or not the target exists / is reachable (anti-enumeration).
    return jsonify({"status": "requested"}), 202


@bp.post("/contact-requests/<int:request_id>/accept")
@login_required
def accept_request(request_id: int):
    req = contact_service.accept_request(g.user, request_id)
    return jsonify({"id": req.id, "status": req.status})


@bp.post("/contact-requests/<int:request_id>/reject")
@login_required
def reject_request(request_id: int):
    req = contact_service.reject_request(g.user, request_id)
    return jsonify({"id": req.id, "status": req.status})


@bp.get("/blocks")
@login_required
def list_blocks():
    return jsonify({"blocks": contact_service.list_blocks(g.user)})


@bp.post("/blocks")
@login_required
def block():
    data = json_body()
    block = contact_service.block_user(g.user, get_int(data, "user_id"))
    return jsonify({"id": block.id, "blocked_id": block.blocked_id}), 201


@bp.delete("/blocks/<int:block_id>")
@login_required
def unblock(block_id: int):
    contact_service.unblock(g.user, block_id)
    return "", 204


@bp.post("/reports")
@login_required
@limiter.limit("10 per hour", key_func=token_key)
def report():
    data = json_body()
    user_id = get_int(data, "user_id", required=False)
    message_id = get_int(data, "message_id", required=False)
    reason = get_str(data, "reason", max_len=1000, min_len=3)
    if user_id is None and message_id is None:
        raise ApiError(400, "invalid_field", "Provide user_id or message_id.")
    if message_id is not None:
        # Only messages the reporter can legitimately see may be reported.
        from app.services import message_service

        msg, _ = message_service._message_for_member(g.user, message_id)
        user_id = user_id or msg.sender_id
    if user_id is not None and db.session.get(User, user_id) is None:
        raise ApiError(404, "not_found", "User not found.")
    row = Report(reporter_id=g.user.id, reported_user_id=user_id, message_id=message_id, reason=reason.strip())
    db.session.add(row)
    db.session.commit()
    return jsonify({"id": row.id, "status": row.status}), 201
