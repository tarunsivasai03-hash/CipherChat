package com.cipherchat.data

import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.common.map
import com.cipherchat.core.common.onSuccess
import com.cipherchat.core.database.ConversationDao
import com.cipherchat.core.database.MessageDao
import com.cipherchat.core.database.ConversationEntity
import com.cipherchat.core.database.MessageEntity
import com.cipherchat.core.database.MessageStatus
import com.cipherchat.core.network.AddMembersRequest
import com.cipherchat.core.network.ApiClient
import com.cipherchat.core.network.ConnectionState
import com.cipherchat.core.network.ConversationDto
import com.cipherchat.core.network.CreateConversationRequest
import com.cipherchat.core.network.MessageDto
import com.cipherchat.core.network.ReactionDto
import com.cipherchat.core.network.ReactionRequest
import com.cipherchat.core.network.RenameRequest
import com.cipherchat.core.network.SeenRequest
import com.cipherchat.core.network.SendMessageRequest
import com.cipherchat.core.network.SocketAck
import com.cipherchat.core.network.SocketEvent
import com.cipherchat.core.network.Realtime
import com.cipherchat.core.network.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Single source of truth for conversations and messages: Room is what the UI observes; the network
 * and socket only write into Room.
 *
 * Sending rules (see docs/ARCHITECTURE.md):
 *  - A message is written to Room as PENDING first, so it survives process death.
 *  - It becomes SENT only after the server acknowledges durable storage (socket ack or REST 2xx).
 *  - "Delivered" is shown only after a delivery receipt event from a recipient's client. There are no read receipts.
 *  - Retries reuse the same client_msg_id, so the server de-duplicates; Room's primary key does the same locally.
 */
class ChatRepository(
    private val api: ApiClient,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val socket: Realtime,
    private val myId: () -> Long?,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val sendMutex = Mutex()
    private var jobs: List<Job> = emptyList()

    val conversations: Flow<List<ConversationEntity>> = conversationDao.observeAll()
    fun conversation(id: Long): Flow<ConversationEntity?> = conversationDao.observe(id)
    fun messages(conversationId: Long): Flow<List<MessageEntity>> = messageDao.observe(conversationId)

    private val _activeConversation = MutableStateFlow<Long?>(null)
    private val _typing = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    val typing: StateFlow<Map<Long, Set<Long>>> = _typing.asStateFlow()
    private val typingStamp = HashMap<Pair<Long, Long>, Long>()

    /** Starts event handling. Idempotent: calling twice does not create duplicate subscriptions. */
    @Synchronized
    fun start() {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch { socket.events.collect { handle(it) } },
            scope.launch {
                socket.state.filter { it == ConnectionState.CONNECTED }.collect {
                    refreshConversations()
                    retryPending()
                }
            },
            scope.launch {
                while (true) { // disappearing messages: drop expired rows locally
                    messageDao.purgeExpired(now())
                    delay(2_000)
                }
            },
        )
    }

    @Synchronized
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        _activeConversation.value = null
        _typing.value = emptyMap()
    }

    // ---- socket events -------------------------------------------------------------------------------

    private suspend fun handle(event: SocketEvent) {
        when (event) {
            is SocketEvent.NewMessage -> onServerMessage(event.message, fromBroadcast = true)
            is SocketEvent.DeliveryReceipt -> if (event.userId != myId()) messageDao.incrementDelivered(event.messageId)
            is SocketEvent.MessageDeleted -> messageDao.deleteByServerId(event.messageId)
            is SocketEvent.ReactionChanged -> messageDao.setReactions(event.messageId, encodeReactions(event.reactions))
            is SocketEvent.ConversationsChanged -> refreshConversations()
            is SocketEvent.Typing -> if (event.userId != myId()) onTyping(event)
            SocketEvent.ContactsChanged, SocketEvent.Unauthorized -> Unit // handled by contacts / session owners
        }
    }

    private fun onTyping(e: SocketEvent.Typing) {
        val key = e.conversationId to e.userId
        _typing.update { m ->
            val set = m[e.conversationId].orEmpty()
            m + (e.conversationId to if (e.isTyping) set + e.userId else set - e.userId)
        }
        if (e.isTyping) {
            val stamp = now().also { typingStamp[key] = it }
            scope.launch {
                delay(5_000)
                if (typingStamp[key] == stamp) onTyping(e.copy(isTyping = false))
            }
        }
    }

    /** Stores a server-confirmed message (REST response, socket ack or broadcast) exactly once. */
    private suspend fun onServerMessage(dto: MessageDto, fromBroadcast: Boolean = false) {
        val alreadyKnown = messageDao.get(dto.clientMsgId)?.serverId == dto.id
        messageDao.upsert(dto.toEntity())
        val mine = dto.senderId == myId()
        val conv = conversationDao.get(dto.conversationId)
        if (conv == null) {
            refreshConversations()
        } else if (!alreadyKnown) {
            val open = _activeConversation.value == dto.conversationId
            conversationDao.upsert(
                conv.copy(
                    lastMessageClientId = dto.clientMsgId,
                    lastMessageSenderId = dto.senderId,
                    lastMessageKind = dto.kind,
                    lastMessagePayload = dto.payload,
                    lastMessageAt = parseTime(dto.createdAt),
                    unreadCount = if (mine || open) conv.unreadCount else conv.unreadCount + 1,
                ),
            )
            if (open && !mine) markSeen(dto.conversationId, dto.id)
        }
        // Tell the sender this client received it. (Delivery evidence only; says nothing about being read.)
        if (fromBroadcast && !mine && !alreadyKnown) socket.acknowledgeDelivery(dto.id)
    }

    // ---- conversations -------------------------------------------------------------------------------

    suspend fun refreshConversations(): AppResult<Unit> =
        apiCall { api.service().conversations().conversations }.map { list ->
            // Keep the local unread count at 0 for the conversation the user is looking at.
            val open = _activeConversation.value
            conversationDao.upsertAll(list.map { it.toEntity().let { e -> if (e.id == open) e.copy(unreadCount = 0) else e } })
            conversationDao.deleteAllExcept(list.map { it.id })
        }

    private suspend fun storeConversation(dto: ConversationDto): Long {
        conversationDao.upsert(dto.toEntity())
        return dto.id
    }

    suspend fun openDirect(userId: Long): AppResult<Long> =
        apiCall { api.service().createConversation(CreateConversationRequest(type = "direct", userId = userId)).conversation }
            .map { storeConversation(it) }

    suspend fun createGroup(name: String, memberIds: List<Long>): AppResult<Long> =
        apiCall { api.service().createConversation(CreateConversationRequest(type = "group", name = name.trim(), memberIds = memberIds)).conversation }
            .map { storeConversation(it) }

    suspend fun refreshConversation(id: Long): AppResult<Unit> =
        apiCall { api.service().conversation(id).conversation }.map { storeConversation(it) }

    suspend fun addMembers(id: Long, memberIds: List<Long>): AppResult<Unit> =
        apiCall { api.service().addMembers(id, AddMembersRequest(memberIds)).conversation }.map { storeConversation(it) }

    suspend fun rename(id: Long, name: String): AppResult<Unit> =
        apiCall { api.service().rename(id, RenameRequest(name.trim())).conversation }.map { storeConversation(it) }

    /** Remove another member (admin) or leave (userId == me). Leaving also removes the local copy. */
    suspend fun removeMember(id: Long, userId: Long): AppResult<Unit> {
        val r = apiCall { api.service().removeMember(id, userId) }
        if (r is AppResult.Success && userId == myId()) {
            messageDao.deleteConversation(id)
            conversationDao.delete(id)
        } else if (r is AppResult.Success) refreshConversation(id)
        return r
    }

    suspend fun setDisappearTimer(id: Long, seconds: Int?): AppResult<Unit> {
        val body = JsonObject(mapOf("disappear_after_seconds" to (seconds?.let { JsonPrimitive(it) } ?: JsonNull)))
        return apiCall { api.service().setDisappear(id, body).conversation }.map { storeConversation(it) }
    }

    // ---- messages ------------------------------------------------------------------------------------

    fun setActiveConversation(id: Long?) {
        val previous = _activeConversation.value
        _activeConversation.value = id
        if (previous != null && previous != id) socket.leaveConversation(previous)
        if (id != null) socket.joinConversation(id)
    }

    /** Only clears if [id] is still the active conversation (a newer chat screen may already have taken over). */
    fun releaseConversation(id: Long) {
        if (_activeConversation.value == id) setActiveConversation(null)
    }

    /** Loads the newest page and reconciles deletions made while this device was away. Returns hasMore. */
    suspend fun loadLatest(conversationId: Long, limit: Int = PAGE_SIZE): AppResult<Boolean> =
        apiCall { api.service().messages(conversationId, null, limit) }.map { page ->
            messageDao.upsertAll(page.messages.map { it.toEntity() })
            val minId = if (page.hasMore) page.messages.minOfOrNull { it.id } ?: 0L else 0L
            val keep = page.messages.map { it.id }.toSet()
            messageDao.serverIdsFrom(conversationId, minId).filter { it !in keep }.forEach { messageDao.deleteByServerId(it) }
            page.hasMore
        }

    suspend fun loadOlder(conversationId: Long, limit: Int = PAGE_SIZE): AppResult<Boolean> {
        val before = messageDao.oldestServerId(conversationId) ?: return AppResult.Success(false)
        return apiCall { api.service().messages(conversationId, before, limit) }.map { page ->
            messageDao.upsertAll(page.messages.map { it.toEntity() })
            page.hasMore
        }
    }

    /** Queues a message. Returns immediately after the PENDING row is durably written, then tries to send. */
    suspend fun send(conversationId: Long, text: String, kind: String, meta: String?, replyToServerId: Long?): String {
        val me = myId() ?: error("not signed in")
        val clientId = newId()
        val pending = MessageEntity(
            clientMsgId = clientId,
            serverId = null,
            conversationId = conversationId,
            senderId = me,
            kind = kind,
            payload = text,
            meta = meta,
            replyToServerId = replyToServerId,
            createdAt = now(),
            expiresAt = null,
            status = MessageStatus.PENDING,
            deliveredCount = 0,
            reactionsJson = "[]",
        )
        messageDao.upsert(pending)
        scope.launch { attempt(pending) }
        return clientId
    }

    /** Re-queues a failed message (user tapped retry) and tries again. */
    suspend fun retry(clientMsgId: String) {
        val m = messageDao.get(clientMsgId) ?: return
        if (m.status == MessageStatus.SENT) return
        messageDao.setStatus(clientMsgId, MessageStatus.PENDING, null)
        attempt(m.copy(status = MessageStatus.PENDING, failureReason = null))
    }

    suspend fun discard(clientMsgId: String) = messageDao.delete(clientMsgId)

    suspend fun retryPending() {
        messageDao.pending().forEach { attempt(it) }
    }

    private suspend fun attempt(m: MessageEntity) = sendMutex.withLock {
        // Re-read: another path may already have confirmed it.
        val current = messageDao.get(m.clientMsgId) ?: return@withLock
        if (current.status != MessageStatus.PENDING) return@withLock
        val request = SendMessageRequest(m.kind, m.payload, m.clientMsgId, m.meta, m.replyToServerId)

        when (val ack = socket.sendMessage(m.conversationId, request)) {
            is SocketAck.Accepted -> {
                if (ack.message != null) return@withLock onServerMessage(ack.message)
                sendViaRest(m, request) // accepted but unparsable ack: confirm idempotently over REST
            }
            is SocketAck.Rejected -> when (ack.code) {
                "rate_limited", "server_error" -> Unit // stay PENDING
                "unauthorized" -> Unit // session handling will sign the user out
                else -> messageDao.setStatus(m.clientMsgId, MessageStatus.FAILED, rejectionText(ack.code))
            }
            SocketAck.Unavailable -> sendViaRest(m, request)
        }
    }

    private suspend fun sendViaRest(m: MessageEntity, request: SendMessageRequest) {
        when (val r = apiCall { api.service().sendMessage(m.conversationId, request).message }) {
            is AppResult.Success -> onServerMessage(r.data)
            is AppResult.Failure -> if (r.error.isDefinitiveRejection) {
                messageDao.setStatus(m.clientMsgId, MessageStatus.FAILED, r.error.message)
            }
        }
    }

    private fun rejectionText(code: String) = when (code) {
        "cannot_message" -> "You can no longer message this person."
        "not_found" -> "This conversation is no longer available."
        "e2e_unavailable" -> "End-to-end encryption isn't available."
        else -> "The server rejected this message ($code)."
    }

    suspend fun markSeen(conversationId: Long, lastServerId: Long) {
        conversationDao.clearUnread(conversationId)
        apiCall { api.service().markSeen(conversationId, SeenRequest(lastServerId)) }
    }

    suspend fun deleteMessage(serverId: Long): AppResult<Unit> =
        apiCall { api.service().deleteMessage(serverId) }.also { it.onSuccess { messageDao.deleteByServerId(serverId) } }

    suspend fun react(serverId: Long, emoji: String?): AppResult<List<ReactionDto>> {
        val r = apiCall { if (emoji == null) api.service().removeReaction(serverId) else api.service().react(serverId, ReactionRequest(emoji)) }
        return r.map { it.reactions }.also { res -> res.onSuccess { messageDao.setReactions(serverId, encodeReactions(it)) } }
    }

    fun sendTyping(conversationId: Long, typing: Boolean) = socket.sendTyping(conversationId, typing)

    companion object {
        const val PAGE_SIZE = 40
    }
}
