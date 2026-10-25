package com.cipherchat.core.network

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import kotlin.coroutines.resume

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

sealed interface SocketEvent {
    data class NewMessage(val message: MessageDto) : SocketEvent
    data class DeliveryReceipt(val conversationId: Long, val messageId: Long, val userId: Long) : SocketEvent
    data class MessageDeleted(val conversationId: Long, val messageId: Long) : SocketEvent
    data class ReactionChanged(val conversationId: Long, val messageId: Long, val reactions: List<ReactionDto>) : SocketEvent
    data class Typing(val conversationId: Long, val userId: Long, val isTyping: Boolean) : SocketEvent
    data object ConversationsChanged : SocketEvent
    data object ContactsChanged : SocketEvent
    /** The server refused the connection or dropped it because the session is no longer valid. */
    data object Unauthorized : SocketEvent
}

/** Result of emitting an event that the server acknowledges. */
sealed interface SocketAck {
    data class Accepted(val message: MessageDto?) : SocketAck
    data class Rejected(val code: String) : SocketAck
    data object Unavailable : SocketAck // not connected / timed out: caller must treat the message as NOT accepted
}

/** What the rest of the app needs from the realtime channel (lets tests substitute a fake). */
interface Realtime {
    val state: StateFlow<ConnectionState>
    val events: SharedFlow<SocketEvent>
    fun joinConversation(id: Long)
    fun leaveConversation(id: Long)
    fun sendTyping(conversationId: Long, typing: Boolean)
    fun acknowledgeDelivery(messageId: Long)
    suspend fun sendMessage(conversationId: Long, request: SendMessageRequest, timeoutMs: Long = 10_000): SocketAck
}

/**
 * Single app-wide Socket.IO connection. A fresh [Socket] is created on every [connect] so a new
 * token is always used, and listeners are registered exactly once per socket (no duplicate subscriptions).
 * The Socket.IO client's built-in reconnection handles network drops; [state] reflects it.
 */
class SocketManager(private val config: ConnectionConfig, private val json: Json = NetworkJson) : Realtime {
    private var socket: Socket? = null
    private val joined = LinkedHashSet<Long>()

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 128)
    override val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    @Synchronized
    fun connect() {
        disconnect()
        val token = config.token ?: return
        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket")
            reconnection = true
            reconnectionDelay = 1_000
            reconnectionDelayMax = 15_000
            timeout = 10_000
        }
        val s = try {
            IO.socket(ApiClient.normalize(config.baseUrl), options)
        } catch (e: Exception) {
            _state.value = ConnectionState.DISCONNECTED
            return
        }
        s.on(Socket.EVENT_CONNECT) {
            _state.value = ConnectionState.CONNECTED
            // Rooms are server-side per connection, so re-join after every (re)connect.
            synchronized(this) { joined.toList() }.forEach { id -> emitJoin(s, id) }
        }
        s.on(Socket.EVENT_DISCONNECT) { _state.value = if (s.connected()) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val reason = args.firstOrNull()?.toString().orEmpty()
            _state.value = ConnectionState.DISCONNECTED
            if (reason.contains("unauthorized", ignoreCase = true)) {
                _events.tryEmit(SocketEvent.Unauthorized)
                s.io().reconnection(false) // retrying with a rejected token is pointless
            }
        }
        s.io().on(io.socket.client.Manager.EVENT_RECONNECT_ATTEMPT) { _state.value = ConnectionState.CONNECTING }

        s.on("new_message") { a -> parse<MessageDto>(a)?.let { _events.tryEmit(SocketEvent.NewMessage(it)) } }
        s.on("delivery_receipt") { a ->
            (a.firstOrNull() as? JSONObject)?.let {
                _events.tryEmit(SocketEvent.DeliveryReceipt(it.getLong("conversation_id"), it.getLong("message_id"), it.getLong("user_id")))
            }
        }
        s.on("message_deleted") { a ->
            (a.firstOrNull() as? JSONObject)?.let { _events.tryEmit(SocketEvent.MessageDeleted(it.getLong("conversation_id"), it.getLong("message_id"))) }
        }
        s.on("reaction_changed") { a ->
            val o = a.firstOrNull() as? JSONObject ?: return@on
            val reactions = runCatching {
                json.decodeFromString<List<ReactionDto>>(o.getJSONArray("reactions").toString())
            }.getOrDefault(emptyList())
            _events.tryEmit(SocketEvent.ReactionChanged(o.getLong("conversation_id"), o.getLong("message_id"), reactions))
        }
        s.on("typing") { a ->
            (a.firstOrNull() as? JSONObject)?.let {
                _events.tryEmit(SocketEvent.Typing(it.getLong("conversation_id"), it.getLong("user_id"), it.optBoolean("is_typing", true)))
            }
        }
        s.on("conversation_updated") { _events.tryEmit(SocketEvent.ConversationsChanged) }
        s.on("contacts_changed") { _events.tryEmit(SocketEvent.ContactsChanged) }
        s.on("contact_request") { _events.tryEmit(SocketEvent.ContactsChanged) }

        socket = s
        _state.value = ConnectionState.CONNECTING
        s.connect()
    }

    @Synchronized
    fun disconnect() {
        socket?.let {
            it.off()
            it.disconnect()
            it.close()
        }
        socket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun joinConversation(id: Long) {
        synchronized(this) { joined.add(id) }
        socket?.takeIf { it.connected() }?.let { emitJoin(it, id) }
    }

    override fun leaveConversation(id: Long) {
        synchronized(this) { joined.remove(id) }
        socket?.takeIf { it.connected() }?.emit("leave_conversation", JSONObject().put("conversation_id", id))
    }

    override fun sendTyping(conversationId: Long, typing: Boolean) {
        socket?.takeIf { it.connected() }?.emit("typing", JSONObject().put("conversation_id", conversationId).put("is_typing", typing))
    }

    override fun acknowledgeDelivery(messageId: Long) {
        socket?.takeIf { it.connected() }?.emit("message_delivered", JSONObject().put("message_id", messageId))
    }

    /** Sends a message and waits for the server's durable-acceptance ack (not delivery, not read). */
    override suspend fun sendMessage(conversationId: Long, request: SendMessageRequest, timeoutMs: Long): SocketAck {
        val s = socket?.takeIf { it.connected() } ?: return SocketAck.Unavailable
        val payload = JSONObject(json.encodeToString(request)).put("conversation_id", conversationId)
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont: CancellableContinuation<SocketAck> ->
                s.emit("send_message", arrayOf<Any>(payload), Ack { args ->
                    val o = args.firstOrNull() as? JSONObject
                    val result = when {
                        o == null -> SocketAck.Unavailable
                        o.optBoolean("ok") -> SocketAck.Accepted(
                            o.optJSONObject("message")?.let { runCatching { json.decodeFromString<MessageDto>(it.toString()) }.getOrNull() },
                        )
                        else -> SocketAck.Rejected(o.optString("error", "rejected"))
                    }
                    if (cont.isActive) cont.resume(result)
                })
            }
        } ?: SocketAck.Unavailable
    }

    private fun emitJoin(s: Socket, id: Long) {
        s.emit("join_conversation", JSONObject().put("conversation_id", id))
    }

    private inline fun <reified T> parse(args: Array<Any?>): T? =
        (args.firstOrNull() as? JSONObject)?.let { runCatching { json.decodeFromString<T>(it.toString()) }.getOrNull() }
}
