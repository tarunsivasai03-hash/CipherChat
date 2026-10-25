from datetime import timedelta

from flask import current_app
from sqlalchemy import delete
from sqlalchemy.exc import IntegrityError

from app.extensions import db
from app.models import Conversation, ConversationMember, Message, MessageReaction, MessageReceipt, User
from app.realtime import emit_to_users
from app.utils import ApiError, iso, utcnow

ALLOWED_KINDS = {"plain", "cipher"}
MAX_PAYLOAD_CHARS = 4000
MAX_PAYLOAD_BYTES = 16 * 1024
CLIENT_MSG_ID_MAX = 64
MAX_EMOJI_CODEPOINTS = 8


def _conv_service():
    from app.services import conversation_service  # avoid import cycle

    return conversation_service


def _contact_service():
    from app.services import contact_service

    return contact_service


def serialize_many(messages: list[Message]) -> list[dict]:
    if not messages:
        return []
    ids = [m.id for m in messages]
    receipts: dict[int, list[int]] = {}
    for r in db.session.query(MessageReceipt).filter(MessageReceipt.message_id.in_(ids)):
        receipts.setdefault(r.message_id, []).append(r.user_id)
    reactions: dict[int, list[dict]] = {}
    for r in db.session.query(MessageReaction).filter(MessageReaction.message_id.in_(ids)):
        reactions.setdefault(r.message_id, []).append({"user_id": r.user_id, "emoji": r.emoji})
    return [
        {
            "id": m.id,
            "conversation_id": m.conversation_id,
            "sender_id": m.sender_id,
            "kind": m.kind,
            "payload_version": m.payload_version,
            "payload": m.payload,
            "meta": m.meta,
            "client_msg_id": m.client_msg_id,
            "reply_to_id": m.reply_to_id,
            "created_at": iso(m.created_at),
            "expires_at": iso(m.expires_at),
            "delivered_to": sorted(receipts.get(m.id, [])),
            "reactions": reactions.get(m.id, []),
        }
        for m in messages
    ]


def purge_expired() -> int:
    result = db.session.execute(delete(Message).where(Message.expires_at.is_not(None), Message.expires_at <= utcnow()))
    db.session.commit()
    return result.rowcount or 0


def _validate_payload(kind: str, payload, meta, client_msg_id) -> None:
    if kind == "e2e":
        # Honest refusal: never store something labelled e2e that this build cannot verify.
        raise ApiError(422, "e2e_unavailable", "End-to-end encrypted messaging is not available on this server.")
    if kind not in ALLOWED_KINDS:
        raise ApiError(400, "invalid_field", "Unsupported message kind.")
    if not isinstance(payload, str) or not (1 <= len(payload) <= MAX_PAYLOAD_CHARS) or len(payload.encode("utf-8")) > MAX_PAYLOAD_BYTES:
        raise ApiError(400, "invalid_field", "Message payload is empty or too long.")
    if meta is not None and (not isinstance(meta, str) or len(meta) > 64):
        raise ApiError(400, "invalid_field", "meta must be a short string.")
    if kind == "cipher" and not meta:
        raise ApiError(400, "invalid_field", "Cipher messages must name the cipher in 'meta'.")
    if not isinstance(client_msg_id, str) or not (8 <= len(client_msg_id) <= CLIENT_MSG_ID_MAX):
        raise ApiError(400, "invalid_field", "client_msg_id must be an 8-64 character string.")


def send_message(user: User, conversation_id: int, *, kind, payload, client_msg_id, meta=None, reply_to_id=None) -> tuple[Message, bool]:
    conv, member = _conv_service().require_member(user.id, conversation_id)
    _validate_payload(kind, payload, meta, client_msg_id)

    if conv.type == "direct":
        other = (
            db.session.query(ConversationMember.user_id)
            .filter(ConversationMember.conversation_id == conv.id, ConversationMember.user_id != user.id)
            .scalar()
        )
        cs = _contact_service()
        if other is None or not cs.are_contacts(user.id, other) or cs.is_blocked_either_way(user.id, other):
            raise ApiError(403, "cannot_message", "You cannot message this user.")

    existing = (
        db.session.query(Message).filter_by(conversation_id=conv.id, sender_id=user.id, client_msg_id=client_msg_id).one_or_none()
    )
    if existing is not None:
        return existing, False

    if reply_to_id is not None:
        parent = db.session.get(Message, reply_to_id) if isinstance(reply_to_id, int) and not isinstance(reply_to_id, bool) else None
        if parent is None or parent.conversation_id != conv.id:
            raise ApiError(400, "invalid_field", "reply_to_id does not refer to a message in this conversation.")

    now = utcnow()
    msg = Message(
        conversation_id=conv.id,
        sender_id=user.id,
        kind=kind,
        payload=payload,
        meta=meta,
        client_msg_id=client_msg_id,
        reply_to_id=reply_to_id,
        created_at=now,
        expires_at=now + timedelta(seconds=conv.disappear_after_seconds) if conv.disappear_after_seconds else None,
    )
    db.session.add(msg)
    try:
        db.session.commit()  # durable before we acknowledge or relay
    except IntegrityError:
        db.session.rollback()
        existing = (
            db.session.query(Message).filter_by(conversation_id=conv.id, sender_id=user.id, client_msg_id=client_msg_id).one_or_none()
        )
        if existing is None:
            raise
        return existing, False

    emit_to_users(_conv_service().active_member_ids(conv.id), "new_message", serialize_many([msg])[0])
    return msg, True


def list_messages(user: User, conversation_id: int, before: int | None, limit: int) -> dict:
    conv, member = _conv_service().require_member(user.id, conversation_id)
    purge_expired()
    now = utcnow()
    q = db.session.query(Message).filter(
        Message.conversation_id == conv.id,
        Message.created_at >= member.joined_at,
        (Message.expires_at.is_(None)) | (Message.expires_at > now),
    )
    if before is not None:
        q = q.filter(Message.id < before)
    rows = q.order_by(Message.id.desc()).limit(limit + 1).all()
    has_more = len(rows) > limit
    rows = rows[:limit]
    rows.reverse()
    return {
        "messages": serialize_many(rows),
        "has_more": has_more,
        "next_before": rows[0].id if has_more and rows else None,
    }


def _message_for_member(user: User, message_id: int) -> tuple[Message, Conversation]:
    msg = db.session.get(Message, message_id)
    if msg is None:
        raise ApiError(404, "not_found", "Message not found.")
    conv, member = _conv_service().require_member(user.id, msg.conversation_id)  # 404 for non-members
    if msg.created_at < member.joined_at or (msg.expires_at is not None and msg.expires_at <= utcnow()):
        raise ApiError(404, "not_found", "Message not found.")
    return msg, conv


def delete_message(user: User, message_id: int) -> None:
    msg, conv = _message_for_member(user, message_id)
    if msg.sender_id != user.id:
        raise ApiError(403, "forbidden", "You can only delete your own messages.")
    cid, mid = conv.id, msg.id
    members = _conv_service().active_member_ids(cid)
    db.session.delete(msg)
    db.session.commit()
    emit_to_users(members, "message_deleted", {"conversation_id": cid, "message_id": mid})


def record_delivery(user: User, message_id: int) -> bool:
    """A recipient client reports it received a message. Returns True if newly recorded."""
    msg, conv = _message_for_member(user, message_id)
    if msg.sender_id == user.id:
        raise ApiError(400, "invalid_target", "Cannot acknowledge your own message.")
    if db.session.get(MessageReceipt, (msg.id, user.id)) is not None:
        return False
    receipt = MessageReceipt(message_id=msg.id, user_id=user.id)
    db.session.add(receipt)
    try:
        db.session.commit()
    except IntegrityError:
        db.session.rollback()
        return False
    emit_to_users(
        [msg.sender_id],
        "delivery_receipt",
        {"conversation_id": conv.id, "message_id": msg.id, "user_id": user.id, "delivered_at": iso(receipt.delivered_at)},
    )
    return True


def set_reaction(user: User, message_id: int, emoji: str | None) -> list[dict]:
    msg, conv = _message_for_member(user, message_id)
    existing = db.session.get(MessageReaction, (msg.id, user.id))
    if emoji is None:
        if existing is not None:
            db.session.delete(existing)
    else:
        emoji = emoji.strip()
        if not emoji or len(emoji) > MAX_EMOJI_CODEPOINTS or any(c.isalnum() and c.isascii() for c in emoji):
            raise ApiError(400, "invalid_field", "Reaction must be a short emoji.")
        if existing is None:
            db.session.add(MessageReaction(message_id=msg.id, user_id=user.id, emoji=emoji))
        else:
            existing.emoji = emoji
    db.session.commit()
    reactions = serialize_many([msg])[0]["reactions"]
    emit_to_users(
        _conv_service().active_member_ids(conv.id),
        "reaction_changed",
        {"conversation_id": conv.id, "message_id": msg.id, "reactions": reactions},
    )
    return reactions


def mark_seen(user: User, conversation_id: int, message_id: int) -> None:
    """Advance the caller's private unread marker. Not shared with anyone."""
    _, member = _conv_service().require_member(user.id, conversation_id)
    if message_id > member.last_seen_message_id:
        member.last_seen_message_id = message_id
        db.session.commit()
