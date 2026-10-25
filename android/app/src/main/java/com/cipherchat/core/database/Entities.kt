package com.cipherchat.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local cache of a conversation summary. Holds only data the server already has in readable form
 * (names, last message preview); never keys or secrets.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val name: String?,
    /** JSON-encoded List<MemberDto>. */
    val membersJson: String,
    val disappearSeconds: Int?,
    val unreadCount: Int,
    val lastMessageClientId: String?,
    val lastMessageSenderId: Long?,
    val lastMessageKind: String?,
    val lastMessagePayload: String?,
    val lastMessageAt: Long?,
    val createdAt: Long,
)

enum class MessageStatus { PENDING, SENT, FAILED }

/**
 * A message row. [clientMsgId] is the primary key, which gives duplicate prevention for free:
 * the optimistic local row, the REST/socket ack and the socket broadcast of the same message all
 * collapse into one row.
 *
 * kind = "plain" | "cipher": both are readable by the server (see docs/E2E_STATUS.md). Rows here are stored
 * unencrypted in the app-private database; cleared on logout and excluded from backups.
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"]), Index(value = ["serverId"], unique = true)],
)
data class MessageEntity(
    @PrimaryKey val clientMsgId: String,
    val serverId: Long?,
    val conversationId: Long,
    val senderId: Long,
    val kind: String,
    val payload: String,
    val meta: String?,
    val replyToServerId: Long?,
    val createdAt: Long,
    val expiresAt: Long?,
    val status: MessageStatus,
    val deliveredCount: Int,
    /** JSON-encoded List<ReactionDto>. */
    val reactionsJson: String,
    val failureReason: String? = null,
)
