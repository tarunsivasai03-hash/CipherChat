package com.cipherchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.network.ConnectionState
import com.cipherchat.core.network.SocketManager
import com.cipherchat.data.AuthRepository
import com.cipherchat.data.ChatRepository
import com.cipherchat.data.members
import com.cipherchat.data.title
import com.cipherchat.domain.EncryptionIndicator
import com.cipherchat.domain.EncryptionPolicy
import com.cipherchat.domain.cipher.Cipher
import com.cipherchat.domain.cipher.CipherOutcome
import com.cipherchat.domain.cipher.CipherRegistry
import com.cipherchat.domain.cipher.CaesarCipher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Composer state. The cipher key lives only here, in memory; it is never persisted or sent. */
data class ComposerState(
    val text: String = "",
    val cipherMode: Boolean = false,
    val cipher: Cipher = CaesarCipher,
    val cipherKey: String = CaesarCipher.exampleKey,
    val error: String? = null,
    val replyTo: MessageUi? = null,
)

data class ChatUiState(
    val title: String = "",
    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val items: List<MessageUi> = emptyList(),
    val loading: Boolean = true,
    val error: AppError? = null,
    val hasMore: Boolean = false,
    val loadingOlder: Boolean = false,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val typingNames: List<String> = emptyList(),
    val disappearSeconds: Int? = null,
    val indicator: EncryptionIndicator = EncryptionIndicator.E2E_UNAVAILABLE,
    val composer: ComposerState = ComposerState(),
    /** Locally decoded cipher messages (message key -> plaintext). Never leaves the device. */
    val decoded: Map<String, String> = emptyMap(),
    val myId: Long? = null,
    val actionError: String? = null,
)

class ChatViewModel(
    private val conversationId: Long,
    private val chat: ChatRepository,
    private val auth: AuthRepository,
    socket: SocketManager,
) : ViewModel() {
    private data class Local(
        val loading: Boolean = true,
        val error: AppError? = null,
        val hasMore: Boolean = false,
        val loadingOlder: Boolean = false,
        val composer: ComposerState = ComposerState(),
        val decoded: Map<String, String> = emptyMap(),
        val actionError: String? = null,
    )

    private val local = MutableStateFlow(Local())
    private var lastSeenMarked = 0L
    private var typingJob: Job? = null
    private var lastTypingSent = 0L

    val state: StateFlow<ChatUiState> = combine(
        chat.messages(conversationId),
        chat.conversation(conversationId),
        local,
        socket.state,
        chat.typing,
    ) { messages, conv, l, conn, typing ->
        val me = auth.currentUserId
        val members = conv?.members().orEmpty()
        val typingNames = typing[conversationId].orEmpty().mapNotNull { id -> members.firstOrNull { it.user.id == id }?.user?.displayName }
        ChatUiState(
            title = conv?.title(me) ?: "Chat",
            isGroup = conv?.type == "group",
            memberCount = members.size,
            items = MessageUiMapper.map(messages, members, me),
            loading = l.loading && messages.isEmpty(),
            error = l.error,
            hasMore = l.hasMore,
            loadingOlder = l.loadingOlder,
            connection = conn,
            typingNames = typingNames,
            disappearSeconds = conv?.disappearSeconds,
            indicator = EncryptionPolicy.forConversation(serverE2eEnabled = false),
            composer = l.composer,
            decoded = l.decoded,
            myId = me,
            actionError = l.actionError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        chat.setActiveConversation(conversationId)
        viewModelScope.launch { loadLatest() }
        viewModelScope.launch { chat.retryPending() }
        // Keep the unread marker current while this conversation is open.
        viewModelScope.launch {
            chat.messages(conversationId).collect { list ->
                val newest = list.mapNotNull { it.serverId }.maxOrNull() ?: return@collect
                if (newest > lastSeenMarked) {
                    lastSeenMarked = newest
                    chat.markSeen(conversationId, newest)
                }
            }
        }
    }

    fun loadLatest() {
        local.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = chat.loadLatest(conversationId)) {
                is AppResult.Success -> local.update { it.copy(loading = false, hasMore = r.data) }
                is AppResult.Failure -> local.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun loadOlder() {
        val l = local.value
        if (!l.hasMore || l.loadingOlder) return
        local.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            when (val r = chat.loadOlder(conversationId)) {
                is AppResult.Success -> local.update { it.copy(loadingOlder = false, hasMore = r.data) }
                is AppResult.Failure -> local.update { it.copy(loadingOlder = false, actionError = r.error.message) }
            }
        }
    }

    // ---- composer ------------------------------------------------------------------------------------

    fun onText(text: String) {
        local.update { it.copy(composer = it.composer.copy(text = text, error = null)) }
        val now = System.currentTimeMillis()
        if (text.isNotEmpty() && now - lastTypingSent > 3_000) {
            lastTypingSent = now
            chat.sendTyping(conversationId, true)
        }
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(4_000)
            chat.sendTyping(conversationId, false)
            lastTypingSent = 0
        }
    }

    fun setCipherMode(on: Boolean) = local.update { it.copy(composer = it.composer.copy(cipherMode = on, error = null)) }

    fun selectCipher(c: Cipher) = local.update { it.copy(composer = it.composer.copy(cipher = c, cipherKey = c.exampleKey, error = null)) }

    fun onCipherKey(key: String) = local.update { it.copy(composer = it.composer.copy(cipherKey = key, error = null)) }

    fun setReply(m: MessageUi?) = local.update { it.copy(composer = it.composer.copy(replyTo = m)) }

    fun send() {
        val c = local.value.composer
        val text = c.text.trim()
        if (text.isEmpty()) return
        var kind = "plain"
        var meta: String? = null
        var payload = text
        if (c.cipherMode) {
            when (val out = c.cipher.encrypt(text, c.cipherKey)) {
                is CipherOutcome.Error -> {
                    local.update { it.copy(composer = it.composer.copy(error = out.message)) }
                    return
                }
                is CipherOutcome.Ok -> {
                    payload = out.text
                    kind = "cipher"
                    meta = c.cipher.id
                }
            }
        }
        val replyTo = c.replyTo?.serverId
        local.update { it.copy(composer = it.composer.copy(text = "", error = null, replyTo = null)) }
        typingJob?.cancel()
        chat.sendTyping(conversationId, false)
        viewModelScope.launch { chat.send(conversationId, payload, kind, meta, replyTo) }
    }

    fun retry(m: MessageUi) { viewModelScope.launch { chat.retry(m.key) } }
    fun discard(m: MessageUi) { viewModelScope.launch { chat.discard(m.key) } }

    // ---- message actions -----------------------------------------------------------------------------

    fun react(m: MessageUi, emoji: String) {
        val id = m.serverId ?: return
        val mineAlready = m.reactions.firstOrNull { it.mine }?.emoji
        viewModelScope.launch {
            val r = chat.react(id, if (mineAlready == emoji) null else emoji)
            if (r is AppResult.Failure) local.update { it.copy(actionError = r.error.message) }
        }
    }

    fun delete(m: MessageUi) {
        val id = m.serverId ?: return
        viewModelScope.launch {
            val r = chat.deleteMessage(id)
            if (r is AppResult.Failure) local.update { it.copy(actionError = r.error.message) }
        }
    }

    /** Decodes locally with a key the user types. Returns an error string, or null on success. */
    fun decode(m: MessageUi, cipherId: String, key: String): String? {
        val cipher = CipherRegistry.byId(cipherId) ?: return "Unknown cipher."
        return when (val out = cipher.decrypt(m.text, key)) {
            is CipherOutcome.Error -> out.message
            is CipherOutcome.Ok -> {
                local.update { it.copy(decoded = it.decoded + (m.key to out.text)) }
                null
            }
        }
    }

    fun hideDecoded(m: MessageUi) = local.update { it.copy(decoded = it.decoded - m.key) }
    fun dismissActionError() = local.update { it.copy(actionError = null) }

    override fun onCleared() {
        chat.releaseConversation(conversationId)
        chat.sendTyping(conversationId, false)
    }
}
