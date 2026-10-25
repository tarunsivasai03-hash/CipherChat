from sqlalchemy import and_, delete, or_

from app.extensions import db
from app.models import Block, Contact, ContactRequest, User
from app.realtime import emit_to_users
from app.utils import ApiError, iso, utcnow


def is_blocked_either_way(a: int, b: int) -> bool:
    return (
        db.session.query(Block.id)
        .filter(or_(and_(Block.blocker_id == a, Block.blocked_id == b), and_(Block.blocker_id == b, Block.blocked_id == a)))
        .first()
        is not None
    )


def are_contacts(a: int, b: int) -> bool:
    return db.session.get(Contact, (a, b)) is not None and db.session.get(Contact, (b, a)) is not None


def _make_contacts(a: int, b: int) -> None:
    for x, y in ((a, b), (b, a)):
        if db.session.get(Contact, (x, y)) is None:
            db.session.add(Contact(user_id=x, contact_user_id=y))


def serialize_request(req: ContactRequest, users: dict[int, User]) -> dict:
    return {
        "id": req.id,
        "status": req.status,
        "sender": users[req.sender_id].public_dict(),
        "recipient": users[req.recipient_id].public_dict(),
        "created_at": iso(req.created_at),
        "updated_at": iso(req.updated_at),
    }


def _users_for(requests) -> dict[int, User]:
    ids = {r.sender_id for r in requests} | {r.recipient_id for r in requests}
    return {u.id: u for u in db.session.query(User).filter(User.id.in_(ids))} if ids else {}


def list_requests(user: User) -> dict:
    incoming = (
        db.session.query(ContactRequest).filter_by(recipient_id=user.id, status="pending").order_by(ContactRequest.id.desc()).all()
    )
    outgoing = (
        db.session.query(ContactRequest).filter_by(sender_id=user.id, status="pending").order_by(ContactRequest.id.desc()).all()
    )
    users = _users_for(incoming + outgoing)
    return {
        "incoming": [serialize_request(r, users) for r in incoming],
        "outgoing": [serialize_request(r, users) for r in outgoing],
    }


def send_request(sender: User, target_id: int | None, target_username: str | None) -> None:
    """Create a contact request.

    The outcome is deliberately indistinguishable (caller always returns 202) whether or not the target
    exists, is discoverable, or has blocked the sender, to limit account/contact enumeration.
    Only errors that concern the sender's own state are reported.
    """
    target = None
    if target_id is not None:
        target = db.session.get(User, target_id)
    elif target_username:
        target = db.session.query(User).filter_by(username_normalized=target_username.strip().lower()).one_or_none()
    if target is not None and target.id == sender.id:
        raise ApiError(400, "invalid_target", "You cannot add yourself.")
    if target is None or target.status != "active":
        return
    if db.session.query(Block.id).filter_by(blocker_id=sender.id, blocked_id=target.id).first():
        raise ApiError(409, "blocked", "Unblock this user first.")
    if db.session.query(Block.id).filter_by(blocker_id=target.id, blocked_id=sender.id).first():
        return
    if are_contacts(sender.id, target.id):
        raise ApiError(409, "already_contacts", "You are already contacts.")

    reverse = db.session.query(ContactRequest).filter_by(sender_id=target.id, recipient_id=sender.id, status="pending").one_or_none()
    if reverse is not None:  # they already asked us: accepting is the natural outcome
        reverse.status = "accepted"
        _make_contacts(sender.id, target.id)
        db.session.commit()
        emit_to_users([sender.id, target.id], "contacts_changed", {})
        return

    existing = db.session.query(ContactRequest).filter_by(sender_id=sender.id, recipient_id=target.id).one_or_none()
    if existing is None:
        db.session.add(ContactRequest(sender_id=sender.id, recipient_id=target.id))
    elif existing.status == "rejected":
        existing.status = "pending"
        existing.updated_at = utcnow()
    db.session.commit()
    emit_to_users([target.id], "contact_request", {"from": sender.public_dict()})


def _own_pending_request(user: User, request_id: int) -> ContactRequest:
    req = db.session.get(ContactRequest, request_id)
    if req is None or req.recipient_id != user.id:
        raise ApiError(404, "not_found", "Request not found.")
    if req.status != "pending":
        raise ApiError(409, "not_pending", "Request is no longer pending.")
    return req


def accept_request(user: User, request_id: int) -> ContactRequest:
    req = _own_pending_request(user, request_id)
    if is_blocked_either_way(req.sender_id, req.recipient_id):
        raise ApiError(409, "blocked", "Cannot accept while a block exists.")
    req.status = "accepted"
    _make_contacts(req.sender_id, req.recipient_id)
    db.session.commit()
    emit_to_users([req.sender_id, req.recipient_id], "contacts_changed", {})
    return req


def reject_request(user: User, request_id: int) -> ContactRequest:
    req = _own_pending_request(user, request_id)
    req.status = "rejected"
    db.session.commit()
    return req


def list_contacts(user: User) -> list[User]:
    return (
        db.session.query(User)
        .join(Contact, Contact.contact_user_id == User.id)
        .filter(Contact.user_id == user.id, User.status == "active")
        .order_by(User.username_normalized)
        .all()
    )


def remove_contact(user: User, other_id: int) -> None:
    if db.session.get(Contact, (user.id, other_id)) is None:
        raise ApiError(404, "not_found", "Contact not found.")
    db.session.execute(
        delete(Contact).where(
            or_(and_(Contact.user_id == user.id, Contact.contact_user_id == other_id), and_(Contact.user_id == other_id, Contact.contact_user_id == user.id))
        )
    )
    db.session.execute(
        delete(ContactRequest).where(
            or_(
                and_(ContactRequest.sender_id == user.id, ContactRequest.recipient_id == other_id),
                and_(ContactRequest.sender_id == other_id, ContactRequest.recipient_id == user.id),
            )
        )
    )
    db.session.commit()
    emit_to_users([user.id, other_id], "contacts_changed", {})


def block_user(user: User, target_id: int) -> Block:
    if target_id == user.id:
        raise ApiError(400, "invalid_target", "You cannot block yourself.")
    target = db.session.get(User, target_id)
    if target is None or target.status != "active":
        raise ApiError(404, "not_found", "User not found.")
    block = db.session.query(Block).filter_by(blocker_id=user.id, blocked_id=target_id).one_or_none()
    if block is None:
        block = Block(blocker_id=user.id, blocked_id=target_id)
        db.session.add(block)
    db.session.execute(
        delete(Contact).where(
            or_(and_(Contact.user_id == user.id, Contact.contact_user_id == target_id), and_(Contact.user_id == target_id, Contact.contact_user_id == user.id))
        )
    )
    db.session.execute(
        delete(ContactRequest).where(
            or_(
                and_(ContactRequest.sender_id == user.id, ContactRequest.recipient_id == target_id),
                and_(ContactRequest.sender_id == target_id, ContactRequest.recipient_id == user.id),
            )
        )
    )
    db.session.commit()
    emit_to_users([user.id, target_id], "contacts_changed", {})
    return block


def unblock(user: User, block_id: int) -> None:
    block = db.session.get(Block, block_id)
    if block is None or block.blocker_id != user.id:
        raise ApiError(404, "not_found", "Block not found.")
    db.session.delete(block)
    db.session.commit()


def list_blocks(user: User) -> list[dict]:
    rows = db.session.query(Block, User).join(User, User.id == Block.blocked_id).filter(Block.blocker_id == user.id).all()
    return [{"id": b.id, "user": u.public_dict(), "created_at": iso(b.created_at)} for b, u in rows]


def search_users(user: User, query: str, limit: int = 20) -> list[User]:
    q = query.strip().lower()
    if len(q) < 3:
        raise ApiError(400, "query_too_short", "Search needs at least 3 characters.")
    escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    blocked_me = db.session.query(Block.blocker_id).filter(Block.blocked_id == user.id)
    return (
        db.session.query(User)
        .filter(
            User.status == "active",
            User.discoverable.is_(True),
            User.id != user.id,
            User.username_normalized.like(escaped + "%", escape="\\"),
            ~User.id.in_(blocked_me),
        )
        .order_by(User.username_normalized)
        .limit(limit)
        .all()
    )
