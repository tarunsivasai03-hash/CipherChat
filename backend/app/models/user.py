from sqlalchemy import Boolean, DateTime, ForeignKey, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.extensions import db
from app.utils import utcnow, iso


class User(db.Model):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    username: Mapped[str] = mapped_column(String(32), nullable=False)
    username_normalized: Mapped[str] = mapped_column(String(32), nullable=False, unique=True)
    display_name: Mapped[str] = mapped_column(String(64), nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    email: Mapped[str | None] = mapped_column(String(254), nullable=True)
    email_normalized: Mapped[str | None] = mapped_column(String(254), nullable=True, unique=True)
    # No verification mail flow is implemented; this stays False. See docs/ARCHITECTURE.md.
    email_verified: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False, default="active")
    discoverable: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)

    def public_dict(self) -> dict:
        return {"id": self.id, "username": self.username, "display_name": self.display_name}

    def self_dict(self) -> dict:
        data = self.public_dict()
        data.update(
            email=self.email,
            email_verified=self.email_verified,
            discoverable=self.discoverable,
            created_at=iso(self.created_at),
        )
        return data


class Device(db.Model):
    """A client installation. One is created per login; revoking it revokes its sessions.

    `identity_public_key` is reserved for the E2E protocol and is always NULL until
    a reviewed protocol is integrated (see docs/E2E_STATUS.md).
    """

    __tablename__ = "devices"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    platform: Mapped[str] = mapped_column(String(32), nullable=False, default="android")
    identity_public_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    status: Mapped[str] = mapped_column(String(16), nullable=False, default="active")
    created_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)
    last_seen_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)
    revoked_at: Mapped[object | None] = mapped_column(DateTime, nullable=True)

    def to_dict(self, current_device_id: int | None = None) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "platform": self.platform,
            "status": self.status,
            "created_at": iso(self.created_at),
            "last_seen_at": iso(self.last_seen_at),
            "revoked_at": iso(self.revoked_at),
            "current": current_device_id == self.id,
        }


class AuthSession(db.Model):
    """Server-side session addressed by an opaque bearer token (only its hash is stored)."""

    __tablename__ = "auth_sessions"
    __table_args__ = (Index("ix_auth_sessions_user", "user_id"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    token_hash: Mapped[str] = mapped_column(String(64), nullable=False, unique=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id", ondelete="CASCADE"), nullable=False, index=True)
    created_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)
    last_used_at: Mapped[object] = mapped_column(DateTime, nullable=False, default=utcnow)
    expires_at: Mapped[object] = mapped_column(DateTime, nullable=False)
    revoked_at: Mapped[object | None] = mapped_column(DateTime, nullable=True)
