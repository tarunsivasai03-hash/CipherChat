from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.extensions import db
from app.utils import utcnow


class Conversation(db.Model):
    __tablename__ = "conversations"
    __table_args__ = (
        CheckConstraint("type IN ('direct','group')", name="ck_conversation_type"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    type: Mapped[str] = mapped_column(String(8), nullable=False)
    creator_id: Mapped[int | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    # Group metadata
    name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    # "<lowerId>:<higherId>" for direct chats; guarantees one direct conversation per pair.
    direct_key: Mapped[str | None] = mapped_column(String(32), nullable=True, unique=True)
    # Disappearing messages: seconds, NULL = disabled.
    disappear_after_seconds: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)


class ConversationMember(db.Model):
    __tablename__ = "conversation_members"
    __table_args__ = (
        CheckConstraint("role IN ('admin','member')", name="ck_member_role"),
        CheckConstraint("status IN ('active','left','removed')", name="ck_member_status"),
    )

    conversation_id: Mapped[int] = mapped_column(
        ForeignKey("conversations.id", ondelete="CASCADE"), primary_key=True
    )
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True, index=True)
    role: Mapped[str] = mapped_column(String(8), nullable=False, default="member")
    status: Mapped[str] = mapped_column(String(8), nullable=False, default="active")
    joined_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)
    left_at: Mapped[object | None] = mapped_column(DateTime, nullable=True)
    # Private to the member: used only to compute their own unread count. It is NOT a read
    # receipt and is never shared with other members.
    last_seen_message_id: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
