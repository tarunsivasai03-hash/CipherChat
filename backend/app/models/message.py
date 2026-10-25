from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.extensions import db
from app.utils import utcnow


class Message(db.Model):
    """A stored message.

    kind:
      * "plain"  - transport-protected only; the server can read it.
      * "cipher" - educational cipher output (Caesar, Vigenere, ...); the server can read it.
      * "e2e"    - reserved. The API rejects it until a reviewed E2E protocol is integrated,
                   so no row with this kind can currently exist.
    """

    __tablename__ = "messages"
    __table_args__ = (
        UniqueConstraint("conversation_id", "sender_id", "client_msg_id", name="uq_message_client_id"),
        Index("ix_messages_conversation_id_id", "conversation_id", "id"),
        Index("ix_messages_expires_at", "expires_at"),
        CheckConstraint("kind IN ('plain','cipher','e2e')", name="ck_message_kind"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    conversation_id: Mapped[int] = mapped_column(ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False)
    sender_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    kind: Mapped[str] = mapped_column(String(8), nullable=False)
    payload_version: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    payload: Mapped[str] = mapped_column(Text, nullable=False)
    # Non-secret descriptor, e.g. the cipher name for kind="cipher". Never a key.
    meta: Mapped[str | None] = mapped_column(String(64), nullable=True)
    client_msg_id: Mapped[str] = mapped_column(String(64), nullable=False)
    reply_to_id: Mapped[int | None] = mapped_column(ForeignKey("messages.id", ondelete="SET NULL"), nullable=True)
    created_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)
    expires_at: Mapped[object | None] = mapped_column(DateTime, nullable=True)


class MessageReceipt(db.Model):
    """Evidence that a recipient's client received the message (delivery only, no read receipts)."""

    __tablename__ = "message_receipts"

    message_id: Mapped[int] = mapped_column(ForeignKey("messages.id", ondelete="CASCADE"), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    delivered_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)


class MessageReaction(db.Model):
    __tablename__ = "message_reactions"

    message_id: Mapped[int] = mapped_column(ForeignKey("messages.id", ondelete="CASCADE"), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    emoji: Mapped[str] = mapped_column(String(16), nullable=False)
    created_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)
