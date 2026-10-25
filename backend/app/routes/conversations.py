from flask import Blueprint, g, jsonify

from app.extensions import db, limiter
from app.models import Conversation
from app.routes.ratelimit import token_key
from app.security.auth import login_required
from app.services import conversation_service as convs
from app.services import message_service as msgs
from app.utils import ApiError, get_int, get_str, json_body, query_int
from flask import request

bp = Blueprint("conversations", __name__, url_prefix="/api/v1")


def _one(conv: Conversation) -> dict:
    return convs.serialize_many(g.user, [conv])[0]


@bp.post("/conversations")
@login_required
def create_conversation():
    data = json_body()
    ctype = get_str(data, "type", max_len=8)
    if ctype == "direct":
        conv, created = convs.get_or_create_direct(g.user, get_int(data, "user_id"))
        return jsonify({"conversation": _one(conv)}), 201 if created else 200
    if ctype == "group":
        ids = data.get("member_ids", [])
        if not isinstance(ids, list):
            raise ApiError(400, "invalid_field", "member_ids must be a list.")
        conv = convs.create_group(g.user, get_str(data, "name", max_len=64, min_len=1), ids)
        return jsonify({"conversation": _one(conv)}), 201
    raise ApiError(400, "invalid_field", "type must be 'direct' or 'group'.")


@bp.get("/conversations")
@login_required
def list_conversations():
    return jsonify({"conversations": convs.list_for_user(g.user)})


@bp.get("/conversations/<int:conversation_id>")
@login_required
def get_conversation(conversation_id: int):
    conv, _ = convs.require_member(g.user.id, conversation_id)
    return jsonify({"conversation": _one(conv)})


@bp.patch("/conversations/<int:conversation_id>")
@login_required
def patch_conversation(conversation_id: int):
    data = json_body()
    if not ({"name", "disappear_after_seconds"} & data.keys()):
        raise ApiError(400, "invalid_json", "Nothing to update.")
    conv = convs.update_conversation(
        g.user,
        conversation_id,
        name=get_str(data, "name", required=False, max_len=64),
        disappear="disappear_after_seconds" in data,
        disappear_value=get_int(data, "disappear_after_seconds", required=False),
    )
    return jsonify({"conversation": _one(conv)})


@bp.post("/conversations/<int:conversation_id>/members")
@login_required
def add_members(conversation_id: int):
    data = json_body()
    ids = data.get("member_ids")
    if not isinstance(ids, list) or not ids:
        raise ApiError(400, "invalid_field", "member_ids must be a non-empty list.")
    conv = convs.add_members(g.user, conversation_id, ids)
    return jsonify({"conversation": _one(conv)})


@bp.delete("/conversations/<int:conversation_id>/members/<int:user_id>")
@login_required
def remove_member(conversation_id: int, user_id: int):
    convs.remove_member(g.user, conversation_id, user_id)
    return "", 204


@bp.get("/conversations/<int:conversation_id>/messages")
@login_required
def list_messages(conversation_id: int):
    before = request.args.get("before")
    if before is not None:
        try:
            before = int(before)
        except ValueError:
            raise ApiError(400, "invalid_query", "'before' must be an integer.")
    return jsonify(msgs.list_messages(g.user, conversation_id, before, query_int("limit", 50)))


@bp.post("/conversations/<int:conversation_id>/messages")
@login_required
@limiter.limit("60 per minute", key_func=token_key)
def send_message(conversation_id: int):
    data = json_body()
    message, created = msgs.send_message(
        g.user,
        conversation_id,
        kind=data.get("kind", "plain"),
        payload=data.get("payload"),
        client_msg_id=data.get("client_msg_id"),
        meta=data.get("meta"),
        reply_to_id=data.get("reply_to_id"),
    )
    return jsonify({"message": msgs.serialize_many([message])[0]}), 201 if created else 200


@bp.post("/conversations/<int:conversation_id>/seen")
@login_required
def mark_seen(conversation_id: int):
    msgs.mark_seen(g.user, conversation_id, get_int(json_body(), "message_id"))
    return "", 204


@bp.delete("/messages/<int:message_id>")
@login_required
def delete_message(message_id: int):
    msgs.delete_message(g.user, message_id)
    return "", 204


@bp.put("/messages/<int:message_id>/reaction")
@login_required
def put_reaction(message_id: int):
    emoji = get_str(json_body(), "emoji", max_len=32, min_len=1)
    return jsonify({"reactions": msgs.set_reaction(g.user, message_id, emoji)})


@bp.delete("/messages/<int:message_id>/reaction")
@login_required
def delete_reaction(message_id: int):
    return jsonify({"reactions": msgs.set_reaction(g.user, message_id, None)})
