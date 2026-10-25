from sqlalchemy import func

from app.extensions import db
from app.models import Conversation, ConversationMember, Message, User
from app.realtime import emit_to_users
from app.services import contact_service, message_service
from app.utils import ApiError, iso, utcnow

MAX_GROUP_MEMBERS = 50
ALLOWED_DISAPPEAR_SECONDS = {10, 60, 3600, 86400}


def require_member(user_id: int, conversation_id: int) -> tuple[Conversation, ConversationMember]:
    """Authorization gate used by every conversation-scoped operation (HTTP and socket).

    Returns 404 (not 403) for non-members so conversation ids cannot be probed.
    """
    conv = db.session.get(Conversation, conversation_id)
    member = db.session.get(ConversationMember, (conversation_id, user_id)) if conv else None
    if conv is None or member is None or member.status != "active":
        raise ApiError(404, "not_found", "Conversation not found.")
    return conv, member


def active_member_ids(conversation_id: int) -> list[int]:
    rows = db.session.query(ConversationMember.user_id).filter_by(conversation_id=conversation_id, status="active").all()
    return [r[0] for r in rows]


def _direct_key(a: int, b: int) -> str:
    return f"{min(a, b)}:{max(a, b)}"


def get_or_create_direct(user: User, other_id: int) -> tuple[Conversation, bool]:
    if other_id == user.id:
        raise ApiError(400, "invalid_target", "Cannot start a conversation with yourself.")
    other = db.session.get(User, other_id)
    if other is None or other.status != "active" or not contact_service.are_contacts(user.id, other_id):
        raise ApiError(403, "not_contacts", "You can only start conversations with contacts.")
    if contact_service.is_blocked_either_way(user.id, other_id):
        raise ApiError(403, "cannot_message", "Conversation is not available.")
    key = _direct_key(user.id, other_id)
    conv = db.session.query(Conversation).filter_by(direct_key=key).one_or_none()
    created = False
    if conv is None:
        conv = Conversation(type="direct", creator_id=user.id, direct_key=key)
        db.session.add(conv)
        db.session.flush()
        db.session.add(ConversationMember(conversation_id=conv.id, user_id=user.id, role="member"))
        db.session.add(ConversationMember(conversation_id=conv.id, user_id=other_id, role="member"))
        created = True
    else:  # re-activate a member who previously had the thread removed (e.g. after contact removal)
        for uid in (user.id, other_id):
            m = db.session.get(ConversationMember, (conv.id, uid))
            if m is not None and m.status != "active":
                m.status, m.left_at, m.joined_at = "active", None, utcnow()
    db.session.commit()
    if created:
        emit_to_users([user.id, other_id], "conversation_updated", {"conversation_id": conv.id})
    return conv, created


def _validate_group_members(creator: User, ids: list[int]) -> list[int]:
    unique = []
    for i in ids:
        if isinstance(i, bool) or not isinstance(i, int):
            raise ApiError(400, "invalid_field", "member_ids must be integers.")
        if i != creator.id and i not in unique:
            unique.append(i)
    for i in unique:
        other = db.session.get(User, i)
        if other is None or other.status != "active" or not contact_service.are_contacts(creator.id, i) or contact_service.is_blocked_either_way(creator.id, i):
            raise ApiError(403, "not_contacts", "Only your contacts can be added to a group.")
    return unique


def create_group(creator: User, name: str, member_ids: list[int]) -> Conversation:
    name = name.strip()
    if not name or len(name) > 64:
        raise ApiError(400, "invalid_field", "Group name must be 1-64 characters.")
    members = _validate_group_members(creator, member_ids)
    if len(members) + 1 > MAX_GROUP_MEMBERS:
        raise ApiError(400, "too_many_members", f"Groups are limited to {MAX_GROUP_MEMBERS} members.")
    conv = Conversation(type="group", creator_id=creator.id, name=name)
    db.session.add(conv)
    db.session.flush()
    db.session.add(ConversationMember(conversation_id=conv.id, user_id=creator.id, role="admin"))
    for uid in members:
        db.session.add(ConversationMember(conversation_id=conv.id, user_id=uid, role="member"))
    db.session.commit()
    emit_to_users([creator.id, *members], "conversation_updated", {"conversation_id": conv.id})
    return conv


def _require_group_admin(user: User, conversation_id: int) -> Conversation:
    conv, member = require_member(user.id, conversation_id)
    if conv.type != "group":
        raise ApiError(400, "not_a_group", "Operation only applies to groups.")
    if member.role != "admin":
        raise ApiError(403, "forbidden", "Only group admins can do this.")
    return conv


def add_members(user: User, conversation_id: int, member_ids: list[int]) -> Conversation:
    conv = _require_group_admin(user, conversation_id)
    new_ids = _validate_group_members(user, member_ids)
    current = set(active_member_ids(conv.id))
    if len(current | set(new_ids)) > MAX_GROUP_MEMBERS:
        raise ApiError(400, "too_many_members", f"Groups are limited to {MAX_GROUP_MEMBERS} members.")
    for uid in new_ids:
        if uid in current:
            continue
        m = db.session.get(ConversationMember, (conv.id, uid))
        if m is None:
            db.session.add(ConversationMember(conversation_id=conv.id, user_id=uid, role="member"))
        else:
            m.status, m.role, m.left_at, m.joined_at, m.last_seen_message_id = "active", "member", None, utcnow(), 0
    db.session.commit()
    emit_to_users(active_member_ids(conv.id), "conversation_updated", {"conversation_id": conv.id})
    return conv


def remove_member(user: User, conversation_id: int, target_id: int) -> None:
    conv, me = require_member(user.id, conversation_id)
    if conv.type != "group":
        raise ApiError(400, "not_a_group", "Operation only applies to groups.")
    target = db.session.get(ConversationMember, (conversation_id, target_id))
    if target is None or target.status != "active":
        raise ApiError(404, "not_found", "Member not found.")
    if target_id != user.id and me.role != "admin":
        raise ApiError(403, "forbidden", "Only group admins can remove members.")
    before = active_member_ids(conversation_id)
    target.status = "left" if target_id == user.id else "removed"
    target.left_at = utcnow()
    db.session.flush()
    remaining = (
        db.session.query(ConversationMember)
        .filter_by(conversation_id=conversation_id, status="active")
        .order_by(ConversationMember.joined_at)
        .all()
    )
    if remaining and not any(m.role == "admin" for m in remaining):
        remaining[0].role = "admin"
    db.session.commit()
    from app.realtime import conversation_room
    from app.sockets import registry

    registry.evict_from_room(target_id, conversation_room(conversation_id))
    emit_to_users(before, "conversation_updated", {"conversation_id": conversation_id})


def update_conversation(user: User, conversation_id: int, *, name=None, disappear=False, disappear_value=None) -> Conversation:
    conv, member = require_member(user.id, conversation_id)
    if name is not None:
        if conv.type != "group" or member.role != "admin":
            raise ApiError(403, "forbidden", "Only group admins can rename a group.")
        name = name.strip()
        if not name or len(name) > 64:
            raise ApiError(400, "invalid_field", "Group name must be 1-64 characters.")
        conv.name = name
    if disappear:
        if conv.type == "group" and member.role != "admin":
            raise ApiError(403, "forbidden", "Only group admins can change disappearing messages.")
        if disappear_value is not None and disappear_value not in ALLOWED_DISAPPEAR_SECONDS:
            raise ApiError(400, "invalid_field", "Unsupported disappearing-message timer.")
        conv.disappear_after_seconds = disappear_value
    db.session.commit()
    emit_to_users(active_member_ids(conv.id), "conversation_updated", {"conversation_id": conv.id})
    return conv


def serialize_many(user: User, convs: list[Conversation]) -> list[dict]:
    if not convs:
        return []
    ids = [c.id for c in convs]
    member_rows = (
        db.session.query(ConversationMember, User)
        .join(User, User.id == ConversationMember.user_id)
        .filter(ConversationMember.conversation_id.in_(ids), ConversationMember.status == "active")
        .all()
    )
    members: dict[int, list[dict]] = {}
    my_seen: dict[int, int] = {}
    my_joined: dict[int, object] = {}
    for m, u in member_rows:
        members.setdefault(m.conversation_id, []).append({"user": u.public_dict(), "role": m.role})
        if u.id == user.id:
            my_seen[m.conversation_id] = m.last_seen_message_id
            my_joined[m.conversation_id] = m.joined_at
    now = utcnow()
    result = []
    for c in convs:
        visible = db.session.query(Message).filter(
            Message.conversation_id == c.id,
            Message.created_at >= my_joined.get(c.id, now),
            (Message.expires_at.is_(None)) | (Message.expires_at > now),
        )
        last = visible.order_by(Message.id.desc()).first()
        unread = visible.filter(Message.id > my_seen.get(c.id, 0), Message.sender_id != user.id).with_entities(func.count()).scalar()
        result.append(
            {
                "id": c.id,
                "type": c.type,
                "name": c.name,
                "creator_id": c.creator_id,
                "disappear_after_seconds": c.disappear_after_seconds,
                "created_at": iso(c.created_at),
                "members": members.get(c.id, []),
                "last_message": message_service.serialize_many([last])[0] if last else None,
                "unread_count": unread or 0,
                # Honest capability flag: no conversation is end-to-end encrypted in this build.
                "encryption": {"e2e": False, "state": "unavailable"},
            }
        )
    return result


def list_for_user(user: User) -> list[dict]:
    message_service.purge_expired()
    convs = (
        db.session.query(Conversation)
        .join(ConversationMember, ConversationMember.conversation_id == Conversation.id)
        .filter(ConversationMember.user_id == user.id, ConversationMember.status == "active")
        .all()
    )
    data = serialize_many(user, convs)
    data.sort(key=lambda d: (d["last_message"] or {}).get("created_at") or d["created_at"], reverse=True)
    return data
