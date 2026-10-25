package com.cipherchat.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.network.ConnectionState
import com.cipherchat.core.network.SocketManager
import com.cipherchat.data.AuthRepository
import com.cipherchat.data.ChatRepository
import com.cipherchat.data.title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationItem(
    val id: Long,
    val title: String,
    val preview: String,
    val timeMs: Long?,
    val unread: Int,
    val isGroup: Boolean,
    val previewIsCipher: Boolean,
)

data class ChatsUiState(
    val items: List<ConversationItem> = emptyList(),
    val query: String = "",
    val refreshing: Boolean = true,
    val error: AppError? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val totalCached: Int = 0,
)

class ChatsViewModel(
    private val chat: ChatRepository,
    private val auth: AuthRepository,
    socket: SocketManager,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val status = MutableStateFlow<Pair<Boolean, AppError?>>(true to null)

    val state: StateFlow<ChatsUiState> = combine(chat.conversations, query, status, socket.state) { convs, q, st, conn ->
        val me = auth.currentUserId
        val all = convs.map { c ->
            val mine = c.lastMessageSenderId == me
            val body = c.lastMessagePayload
            ConversationItem(
                id = c.id,
                title = c.title(me),
                preview = when {
                    body == null -> "No messages yet"
                    mine -> "You: $body"
                    else -> body
                },
                timeMs = c.lastMessageAt,
                unread = c.unreadCount,
                isGroup = c.type == "group",
                previewIsCipher = c.lastMessageKind == "cipher",
            )
        }
        val filtered = if (q.isBlank()) all else all.filter { it.title.contains(q.trim(), ignoreCase = true) || it.preview.contains(q.trim(), ignoreCase = true) }
        ChatsUiState(filtered, q, st.first, st.second, conn, all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUiState())

    init { refresh() }

    fun onQuery(q: String) { query.value = q }

    fun refresh() {
        status.update { true to null }
        viewModelScope.launch {
            val r = chat.refreshConversations()
            status.value = false to (r as? AppResult.Failure)?.error
        }
    }
}
