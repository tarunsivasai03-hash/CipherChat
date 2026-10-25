package com.cipherchat.data

import com.cipherchat.core.database.ConversationEntity
import com.cipherchat.core.database.MessageEntity
import com.cipherchat.core.database.MessageStatus
import com.cipherchat.core.network.ConversationDto
import com.cipherchat.core.network.MemberDto
import com.cipherchat.core.network.MessageDto
import com.cipherchat.core.network.NetworkJson
import com.cipherchat.core.network.ReactionDto
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant

fun parseTime(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

fun MessageDto.toEntity(): MessageEntity = MessageEntity(
    clientMsgId = clientMsgId,
    serverId = id,
    conversationId = conversationId,
    senderId = senderId,
    kind = kind,
    payload = payload,
    meta = meta,
    replyToServerId = replyToId,
    createdAt = parseTime(createdAt) ?: System.currentTimeMillis(),
    expiresAt = parseTime(expiresAt),
    status = MessageStatus.SENT,
    deliveredCount = deliveredTo.size,
    reactionsJson = encodeReactions(reactions),
)

fun ConversationDto.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    type = type,
    name = name,
    membersJson = NetworkJson.encodeToString(ListSerializer(MemberDto.serializer()), members),
    disappearSeconds = disappearAfterSeconds,
    unreadCount = unreadCount,
    lastMessageClientId = lastMessage?.clientMsgId,
    lastMessageSenderId = lastMessage?.senderId,
    lastMessageKind = lastMessage?.kind,
    lastMessagePayload = lastMessage?.payload,
    lastMessageAt = lastMessage?.let { parseTime(it.createdAt) },
    createdAt = parseTime(createdAt) ?: 0L,
)

fun ConversationEntity.members(): List<MemberDto> =
    runCatching { NetworkJson.decodeFromString(ListSerializer(MemberDto.serializer()), membersJson) }.getOrDefault(emptyList())

fun encodeReactions(list: List<ReactionDto>): String = NetworkJson.encodeToString(ListSerializer(ReactionDto.serializer()), list)

fun MessageEntity.reactions(): List<ReactionDto> =
    runCatching { NetworkJson.decodeFromString(ListSerializer(ReactionDto.serializer()), reactionsJson) }.getOrDefault(emptyList())

/** Display title: group name, or the other member's display name for direct chats. */
fun ConversationEntity.title(myId: Long?): String =
    if (type == "group") name ?: "Group" else members().firstOrNull { it.user.id != myId }?.user?.displayName ?: "Conversation"

fun ConversationEntity.peer(myId: Long?): MemberDto? = if (type == "direct") members().firstOrNull { it.user.id != myId } else null
