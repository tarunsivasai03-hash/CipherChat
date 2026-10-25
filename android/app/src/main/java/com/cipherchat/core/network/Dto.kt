package com.cipherchat.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    val discoverable: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable data class RegisterRequest(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
)

@Serializable data class LoginRequest(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
)

@Serializable data class AuthResponse(
    val token: String,
    @SerialName("device_id") val deviceId: Long,
    val user: UserDto,
)

@Serializable data class MeResponse(val user: UserDto, @SerialName("device_id") val deviceId: Long)
@Serializable data class UpdateProfileRequest(@SerialName("display_name") val displayName: String? = null)
@Serializable data class UserEnvelope(val user: UserDto)

@Serializable data class UsersResponse(val users: List<UserDto>)
@Serializable data class ContactsResponse(val contacts: List<UserDto>)

@Serializable data class ContactRequestDto(
    val id: Long,
    val status: String,
    val sender: UserDto,
    val recipient: UserDto,
)

@Serializable data class ContactRequestsResponse(
    val incoming: List<ContactRequestDto>,
    val outgoing: List<ContactRequestDto>,
)

@Serializable data class CreateContactRequest(@SerialName("user_id") val userId: Long)
@Serializable data class BlockRequest(@SerialName("user_id") val userId: Long)
@Serializable data class BlockDto(val id: Long, val user: UserDto)
@Serializable data class BlocksResponse(val blocks: List<BlockDto>)

@Serializable data class ReactionDto(@SerialName("user_id") val userId: Long, val emoji: String)

@Serializable
data class MessageDto(
    val id: Long,
    @SerialName("conversation_id") val conversationId: Long,
    @SerialName("sender_id") val senderId: Long,
    val kind: String,
    @SerialName("payload_version") val payloadVersion: Int = 1,
    val payload: String,
    val meta: String? = null,
    @SerialName("client_msg_id") val clientMsgId: String,
    @SerialName("reply_to_id") val replyToId: Long? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("delivered_to") val deliveredTo: List<Long> = emptyList(),
    val reactions: List<ReactionDto> = emptyList(),
)

@Serializable data class MessagesPage(
    val messages: List<MessageDto>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_before") val nextBefore: Long? = null,
)

@Serializable data class MessageEnvelope(val message: MessageDto)

@Serializable data class SendMessageRequest(
    val kind: String,
    val payload: String,
    @SerialName("client_msg_id") val clientMsgId: String,
    val meta: String? = null,
    @SerialName("reply_to_id") val replyToId: Long? = null,
)

@Serializable data class MemberDto(val user: UserDto, val role: String)
@Serializable data class EncryptionDto(val e2e: Boolean = false, val state: String = "unavailable")

@Serializable
data class ConversationDto(
    val id: Long,
    val type: String,
    val name: String? = null,
    @SerialName("creator_id") val creatorId: Long? = null,
    @SerialName("disappear_after_seconds") val disappearAfterSeconds: Int? = null,
    @SerialName("created_at") val createdAt: String,
    val members: List<MemberDto> = emptyList(),
    @SerialName("last_message") val lastMessage: MessageDto? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    val encryption: EncryptionDto = EncryptionDto(),
)

@Serializable data class ConversationsResponse(val conversations: List<ConversationDto>)
@Serializable data class ConversationEnvelope(val conversation: ConversationDto)

@Serializable data class CreateConversationRequest(
    val type: String,
    @SerialName("user_id") val userId: Long? = null,
    val name: String? = null,
    @SerialName("member_ids") val memberIds: List<Long>? = null,
)

@Serializable data class AddMembersRequest(@SerialName("member_ids") val memberIds: List<Long>)

@Serializable data class RenameRequest(val name: String)
@Serializable data class SeenRequest(@SerialName("message_id") val messageId: Long)
@Serializable data class ReactionRequest(val emoji: String)
@Serializable data class ReactionsResponse(val reactions: List<ReactionDto>)

@Serializable data class DeviceDto(
    val id: Long,
    val name: String,
    val platform: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    val current: Boolean = false,
)

@Serializable data class DevicesResponse(val devices: List<DeviceDto>)
@Serializable data class DeviceEnvelope(val device: DeviceDto)

@Serializable data class SettingsDto(val discoverable: Boolean = true)
@Serializable data class SettingsEnvelope(val settings: SettingsDto)
@Serializable data class UpdateSettingsRequest(val discoverable: Boolean)
@Serializable data class DeleteAccountRequest(val password: String)

@Serializable data class HealthDto(
    val status: String,
    @SerialName("e2e_enabled") val e2eEnabled: Boolean = false,
)

@Serializable data class ErrorInfo(val code: String? = null, val message: String? = null)
@Serializable data class ErrorBody(val error: ErrorInfo? = null)
