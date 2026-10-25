package com.cipherchat.testing

import com.cipherchat.core.database.ConversationDao
import com.cipherchat.core.database.ConversationEntity
import com.cipherchat.core.database.MessageDao
import com.cipherchat.core.database.MessageEntity
import com.cipherchat.core.database.MessageStatus
import com.cipherchat.core.network.ConnectionConfig
import com.cipherchat.core.network.ConnectionState
import com.cipherchat.core.network.Realtime
import com.cipherchat.core.network.SendMessageRequest
import com.cipherchat.core.network.SocketAck
import com.cipherchat.core.network.SocketEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeConversationDao : ConversationDao {
    val rows = MutableStateFlow<Map<Long, ConversationEntity>>(emptyMap())
    override fun observeAll(): Flow<List<ConversationEntity>> = rows.map { it.values.toList() }
    override fun observe(id: Long): Flow<ConversationEntity?> = rows.map { it[id] }
    override suspend fun get(id: Long) = rows.value[id]
    override suspend fun upsertAll(items: List<ConversationEntity>) { rows.value = rows.value + items.associateBy { it.id } }
    override suspend fun upsert(item: ConversationEntity) { rows.value = rows.value + (item.id to item) }
    override suspend fun deleteAllExcept(keep: List<Long>) { rows.value = rows.value.filterKeys { it in keep } }
    override suspend fun delete(id: Long) { rows.value = rows.value - id }
    override suspend fun clearUnread(id: Long) { rows.value[id]?.let { rows.value = rows.value + (id to it.copy(unreadCount = 0)) } }
    override suspend fun incrementUnread(id: Long) { rows.value[id]?.let { rows.value = rows.value + (id to it.copy(unreadCount = it.unreadCount + 1)) } }
    override suspend fun clear() { rows.value = emptyMap() }
}

/** In-memory MessageDao keyed by clientMsgId, like the real primary key. */
class FakeMessageDao : MessageDao {
    val rows = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())

    private fun update(id: String, f: (MessageEntity) -> MessageEntity) { rows.value[id]?.let { rows.value = rows.value + (id to f(it)) } }
    private fun byServer(serverId: Long) = rows.value.values.firstOrNull { it.serverId == serverId }

    override fun observe(conversationId: Long): Flow<List<MessageEntity>> = rows.map { m ->
        m.values.filter { it.conversationId == conversationId }
            .sortedWith(compareBy<MessageEntity>({ if (it.serverId == null) 1 else 0 }, { it.serverId ?: 0L }, { it.createdAt }))
    }
    override suspend fun get(clientMsgId: String) = rows.value[clientMsgId]
    override suspend fun getByServerId(serverId: Long) = byServer(serverId)
    override suspend fun upsert(item: MessageEntity) { rows.value = rows.value + (item.clientMsgId to item) }
    override suspend fun upsertAll(items: List<MessageEntity>) { rows.value = rows.value + items.associateBy { it.clientMsgId } }
    override suspend fun pending() = rows.value.values.filter { it.status == MessageStatus.PENDING }.sortedBy { it.createdAt }
    override suspend fun oldestServerId(conversationId: Long) = rows.value.values.filter { it.conversationId == conversationId }.mapNotNull { it.serverId }.minOrNull()
    override suspend fun newestServerId(conversationId: Long) = rows.value.values.filter { it.conversationId == conversationId }.mapNotNull { it.serverId }.maxOrNull()
    override suspend fun setStatus(clientMsgId: String, status: MessageStatus, reason: String?) = update(clientMsgId) { it.copy(status = status, failureReason = reason) }
    override suspend fun incrementDelivered(serverId: Long) { byServer(serverId)?.let { e -> update(e.clientMsgId) { it.copy(deliveredCount = it.deliveredCount + 1) } } }
    override suspend fun setReactions(serverId: Long, json: String) { byServer(serverId)?.let { e -> update(e.clientMsgId) { it.copy(reactionsJson = json) } } }
    override suspend fun serverIdsFrom(conversationId: Long, minId: Long) =
        rows.value.values.filter { it.conversationId == conversationId && it.serverId != null && it.serverId >= minId }.mapNotNull { it.serverId }
    override suspend fun deleteByServerId(serverId: Long) { byServer(serverId)?.let { rows.value = rows.value - it.clientMsgId } }
    override suspend fun delete(clientMsgId: String) { rows.value = rows.value - clientMsgId }
    override suspend fun deleteConversation(conversationId: Long) { rows.value = rows.value.filterValues { it.conversationId != conversationId } }
    override suspend fun purgeExpired(now: Long): Int {
        val expired = rows.value.filterValues { it.expiresAt != null && it.expiresAt <= now }
        rows.value = rows.value - expired.keys
        return expired.size
    }
    override suspend fun clear() { rows.value = emptyMap() }
}

class FakeRealtime : Realtime {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    /** What the next sendMessage call returns. Defaults to "socket not connected". */
    var ackProvider: (SendMessageRequest) -> SocketAck = { SocketAck.Unavailable }
    val sent = mutableListOf<SendMessageRequest>()
    val joined = mutableListOf<Long>()
    val left = mutableListOf<Long>()
    val delivered = mutableListOf<Long>()
    val typing = mutableListOf<Pair<Long, Boolean>>()

    fun setState(s: ConnectionState) { _state.value = s }
    suspend fun emit(e: SocketEvent) = _events.emit(e)

    override fun joinConversation(id: Long) { joined += id }
    override fun leaveConversation(id: Long) { left += id }
    override fun sendTyping(conversationId: Long, typing: Boolean) { this.typing += conversationId to typing }
    override fun acknowledgeDelivery(messageId: Long) { delivered += messageId }
    override suspend fun sendMessage(conversationId: Long, request: SendMessageRequest, timeoutMs: Long): SocketAck {
        sent += request
        return ackProvider(request)
    }
}

class FakeConnectionConfig(override var baseUrl: String, override var token: String? = "test-token") : ConnectionConfig
