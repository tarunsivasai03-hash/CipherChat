package com.cipherchat.feature.chat

import com.cipherchat.core.database.MessageEntity
import com.cipherchat.core.database.MessageStatus
import com.cipherchat.core.network.MemberDto
import com.cipherchat.data.reactions

/**
 * What the sender may truthfully be told about their message. There is deliberately no READ state:
 * the protocol has no read receipts, so the UI must not imply one.
 */
enum class DeliveryLabel { SENDING, SENT, DELIVERED, FAILED }

data class ReactionGroup(val emoji: String, val count: Int, val mine: Boolean)

data class MessageUi(
    val key: String,
    val serverId: Long?,
    val mine: Boolean,
    val senderName: String,
    val kind: String,
    val meta: String?,
    val text: String,
    val timeMs: Long,
    val label: DeliveryLabel?,
    val deliveredCount: Int,
    val failureReason: String?,
    val reactions: List<ReactionGroup>,
    val replyPreview: String?,
    val expiresAt: Long?,
)

object MessageUiMapper {
    fun labelFor(e: MessageEntity): DeliveryLabel = when (e.status) {
        MessageStatus.PENDING -> DeliveryLabel.SENDING
        MessageStatus.FAILED -> DeliveryLabel.FAILED
        MessageStatus.SENT -> if (e.deliveredCount > 0) DeliveryLabel.DELIVERED else DeliveryLabel.SENT
    }

    fun map(entities: List<MessageEntity>, members: List<MemberDto>, myId: Long?): List<MessageUi> {
        val names = members.associate { it.user.id to it.user.displayName }
        val byServerId = entities.filter { it.serverId != null }.associateBy { it.serverId }
        return entities.map { e ->
            val mine = e.senderId == myId
            val groups = e.reactions().groupBy { it.emoji }.map { (emoji, list) -> ReactionGroup(emoji, list.size, list.any { it.userId == myId }) }
            MessageUi(
                key = e.clientMsgId,
                serverId = e.serverId,
                mine = mine,
                senderName = if (mine) "You" else names[e.senderId] ?: "Former member",
                kind = e.kind,
                meta = e.meta,
                text = e.payload,
                timeMs = e.createdAt,
                label = if (mine) labelFor(e) else null,
                deliveredCount = e.deliveredCount,
                failureReason = e.failureReason,
                reactions = groups,
                replyPreview = e.replyToServerId?.let { byServerId[it]?.payload?.take(80) ?: "Original message" },
                expiresAt = e.expiresAt,
            )
        }
    }
}

/** What the conversation list actually renders: day separators and messages annotated with their place in a run. */
sealed interface ChatRow {
    val key: String

    data class Day(val epochDay: Long, val label: String) : ChatRow {
        override val key: String get() = "day-$epochDay"
    }

    data class Msg(val m: MessageUi, val firstInRun: Boolean, val lastInRun: Boolean) : ChatRow {
        override val key: String get() = m.key
    }
}

object ChatRows {
    private const val RUN_GAP_MS = 5 * 60 * 1000L

    /**
     * Inserts a separator whenever the calendar day changes and marks consecutive messages from the same sender
     * (within five minutes, same day) as one run, so bubbles can join up and only the last one gets a tail.
     */
    fun build(items: List<MessageUi>, dayLabel: (Long) -> String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<ChatRow> {
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
        fun sameRun(a: MessageUi, b: MessageUi) = a.mine == b.mine && a.senderName == b.senderName && day(a.timeMs) == day(b.timeMs) &&
            kotlin.math.abs(b.timeMs - a.timeMs) <= RUN_GAP_MS
        val out = ArrayList<ChatRow>(items.size + 4)
        items.forEachIndexed { i, m ->
            val prev = items.getOrNull(i - 1)
            val next = items.getOrNull(i + 1)
            if (prev == null || day(prev.timeMs) != day(m.timeMs)) out += ChatRow.Day(day(m.timeMs), dayLabel(m.timeMs))
            out += ChatRow.Msg(m, firstInRun = prev == null || !sameRun(prev, m), lastInRun = next == null || !sameRun(m, next))
        }
        return out
    }
}
