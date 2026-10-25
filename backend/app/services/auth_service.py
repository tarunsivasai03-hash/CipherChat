import re
from datetime import timedelta

from flask import current_app
from sqlalchemy import delete, or_, select, update
from sqlalchemy.exc import IntegrityError

from app.extensions import db
from app.models import (
    AuthSession,
    Block,
    Contact,
    ContactRequest,
    Conversation,
    ConversationMember,
    Device,
    Message,
    Report,
    User,
)
from app.security import passwords
from app.security.auth import new_token
from app.utils import ApiError, utcnow

USERNAME_RE = re.compile(r"^[A-Za-z0-9_.]{3,32}$")
EMAIL_RE = re.compile(r"^[^@\s]{1,64}@[^@\s]{1,189}\.[^@\s]{2,}$")


def normalize_username(username: str) -> str:
    return username.strip().lower()


def validate_password(password: str, username: str) -> None:
    if not (passwords.MIN_PASSWORD_LENGTH <= len(password) <= passwords.MAX_PASSWORD_LENGTH):
        raise ApiError(
            400,
            "weak_password",
            f"Password must be {passwords.MIN_PASSWORD_LENGTH}-{passwords.MAX_PASSWORD_LENGTH} characters.",
        )
    if password.lower() == username.lower():
        raise ApiError(400, "weak_password", "Password must not equal the username.")


def _clean_display_name(display_name: str | None, fallback: str) -> str:
    name = (display_name or fallback).strip()
    if not name or len(name) > 64 or any(ord(c) < 32 for c in name):
        raise ApiError(400, "invalid_field", "Display name must be 1-64 printable characters.")
    return name


def _clean_email(email: str | None) -> tuple[str | None, str | None]:
    if email is None or email.strip() == "":
        return None, None
    email = email.strip()
    if len(email) > 254 or not EMAIL_RE.match(email):
        raise ApiError(400, "invalid_field", "Email address is invalid.")
    return email, email.lower()


def register(username: str, password: str, display_name: str | None, email: str | None) -> User:
    if not USERNAME_RE.match(username):
        raise ApiError(400, "invalid_username", "Username must be 3-32 letters, digits, '_' or '.'.")
    validate_password(password, username)
    clean_email, email_norm = _clean_email(email)
    user = User(
        username=username,
        username_normalized=normalize_username(username),
        display_name=_clean_display_name(display_name, username),
        password_hash=passwords.hash_password(password),
        email=clean_email,
        email_normalized=email_norm,
    )
    db.session.add(user)
    try:
        db.session.commit()
    except IntegrityError:
        db.session.rollback()
        raise ApiError(409, "username_taken", "That username or email is not available.")
    return user


def _create_session(user: User, device_name: str, platform: str) -> tuple[str, AuthSession, Device]:
    cfg = current_app.config
    now = utcnow()
    device = Device(user_id=user.id, name=device_name, platform=platform, created_at=now, last_seen_at=now)
    db.session.add(device)
    db.session.flush()
    token, token_hash = new_token()
    session = AuthSession(
        token_hash=token_hash,
        user_id=user.id,
        device_id=device.id,
        created_at=now,
        last_used_at=now,
        expires_at=now + timedelta(seconds=cfg["SESSION_IDLE_TTL_SECONDS"]),
    )
    db.session.add(session)
    db.session.commit()
    return token, session, device


def start_session(user: User, device_name: str, platform: str):
    return _create_session(user, device_name.strip()[:64] or "Android device", platform.strip()[:32] or "android")


def login(username: str, password: str, device_name: str, platform: str):
    user = db.session.query(User).filter_by(username_normalized=normalize_username(username)).one_or_none()
    if user is None or user.status != "active":
        passwords.burn_verification_time(password)
        raise ApiError(401, "invalid_credentials", "Invalid username or password.")
    if not passwords.verify_password(user.password_hash, password):
        raise ApiError(401, "invalid_credentials", "Invalid username or password.")
    if passwords.needs_rehash(user.password_hash):
        user.password_hash = passwords.hash_password(password)
        db.session.commit()
    token, session, device = start_session(user, device_name, platform)
    return user, token, session, device


def revoke_session(session: AuthSession) -> None:
    if session.revoked_at is None:
        session.revoked_at = utcnow()
        db.session.commit()
    from app.sockets.registry import disconnect_session

    disconnect_session(session.id)


def revoke_device(user: User, device_id: int) -> Device:
    device = db.session.get(Device, device_id)
    if device is None or device.user_id != user.id:
        raise ApiError(404, "not_found", "Device not found.")
    now = utcnow()
    device.status = "revoked"
    device.revoked_at = now
    sessions = db.session.query(AuthSession).filter_by(device_id=device.id, revoked_at=None).all()
    ids = [s.id for s in sessions]
    for s in sessions:
        s.revoked_at = now
    db.session.commit()
    from app.sockets.registry import disconnect_session

    for sid in ids:
        disconnect_session(sid)
    return device


def update_profile(user: User, display_name: str | None, email: str | None, email_given: bool) -> User:
    if display_name is not None:
        user.display_name = _clean_display_name(display_name, user.username)
    if email_given:
        clean, norm = _clean_email(email)
        if norm != user.email_normalized:
            user.email, user.email_normalized, user.email_verified = clean, norm, False
    try:
        db.session.commit()
    except IntegrityError:
        db.session.rollback()
        raise ApiError(409, "email_taken", "That email is not available.")
    return user


def delete_account(user: User, password: str) -> None:
    """Hard-delete the account and everything it owns.

    * Sessions, devices, contacts, requests, blocks, reactions, receipts: removed.
    * Messages sent by the user: removed from every conversation.
    * Direct conversations involving the user: removed entirely (the peer loses the thread).
    * Group conversations: membership removed; if the last admin leaves, the oldest member is promoted.
    """
    if not passwords.verify_password(user.password_hash, password):
        raise ApiError(403, "invalid_credentials", "Password is incorrect.")
    uid = user.id
    from app.sockets.registry import disconnect_user

    direct_ids = [
        c.id
        for c in db.session.query(Conversation)
        .join(ConversationMember, ConversationMember.conversation_id == Conversation.id)
        .filter(ConversationMember.user_id == uid, Conversation.type == "direct")
    ]
    group_ids = [
        m.conversation_id
        for m in db.session.query(ConversationMember)
        .join(Conversation, Conversation.id == ConversationMember.conversation_id)
        .filter(ConversationMember.user_id == uid, Conversation.type == "group")
    ]
    if direct_ids:
        db.session.execute(delete(Conversation).where(Conversation.id.in_(direct_ids)))
    db.session.execute(delete(Report).where(Report.reporter_id == uid))
    db.session.execute(update(Report).where(Report.reported_user_id == uid).values(reported_user_id=None))
    db.session.execute(delete(Message).where(Message.sender_id == uid))
    db.session.execute(delete(ConversationMember).where(ConversationMember.user_id == uid))
    db.session.flush()
    for gid in group_ids:
        remaining = (
            db.session.query(ConversationMember)
            .filter_by(conversation_id=gid, status="active")
            .order_by(ConversationMember.joined_at)
            .all()
        )
        if not remaining:
            db.session.execute(delete(Conversation).where(Conversation.id == gid))
        elif not any(m.role == "admin" for m in remaining):
            remaining[0].role = "admin"
    db.session.execute(delete(Contact).where(or_(Contact.user_id == uid, Contact.contact_user_id == uid)))
    db.session.execute(
        delete(ContactRequest).where(or_(ContactRequest.sender_id == uid, ContactRequest.recipient_id == uid))
    )
    db.session.execute(delete(Block).where(or_(Block.blocker_id == uid, Block.blocked_id == uid)))
    db.session.execute(delete(AuthSession).where(AuthSession.user_id == uid))
    db.session.execute(delete(Device).where(Device.user_id == uid))
    db.session.execute(delete(User).where(User.id == uid))
    db.session.commit()
    disconnect_user(uid)
