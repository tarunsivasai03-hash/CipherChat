package com.cipherchat.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.network.ContactRequestDto
import com.cipherchat.core.network.SocketEvent
import com.cipherchat.core.network.SocketManager
import com.cipherchat.core.network.UserDto
import com.cipherchat.data.ChatRepository
import com.cipherchat.data.ContactsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val contacts: List<UserDto> = emptyList(),
    val incoming: List<ContactRequestDto> = emptyList(),
    val outgoing: List<ContactRequestDto> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
    val results: List<UserDto> = emptyList(),
    val searchError: String? = null,
    /** Users we've asked this session. The server answers identically for unknown users, so this is "asked", not "exists". */
    val requestedIds: Set<Long> = emptySet(),
    val message: String? = null,
    val openChatId: Long? = null,
)

class ContactsViewModel(
    private val contacts: ContactsRepository,
    private val chat: ChatRepository,
    socket: SocketManager,
) : ViewModel() {
    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        refresh()
        viewModelScope.launch { socket.events.filter { it is SocketEvent.ContactsChanged }.collect { refresh(silent = true) } }
    }

    fun refresh(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val c = contacts.contacts()
            val r = contacts.requests()
            when {
                c is AppResult.Failure -> _state.update { it.copy(loading = false, error = c.error) }
                r is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error) }
                c is AppResult.Success && r is AppResult.Success ->
                    _state.update { it.copy(loading = false, error = null, contacts = c.data, incoming = r.data.incoming, outgoing = r.data.outgoing) }
            }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q, searchError = null) }
        searchJob?.cancel()
        if (q.trim().length < 3) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce: the server rate-limits search
            _state.update { it.copy(searching = true) }
            when (val r = contacts.search(q)) {
                is AppResult.Success -> _state.update { it.copy(searching = false, results = r.data) }
                is AppResult.Failure -> _state.update { it.copy(searching = false, results = emptyList(), searchError = r.error.message) }
            }
        }
    }

    fun sendRequest(user: UserDto) {
        viewModelScope.launch {
            when (val r = contacts.sendRequest(user.id)) {
                is AppResult.Success -> _state.update { it.copy(requestedIds = it.requestedIds + user.id, message = "Request sent to @${user.username}.") }
                is AppResult.Failure -> _state.update { it.copy(message = r.error.message) }
            }
            refresh(silent = true)
        }
    }

    fun accept(req: ContactRequestDto) = act(null) { contacts.accept(req.id) }
    fun reject(req: ContactRequestDto) = act(null) { contacts.reject(req.id) }
    fun remove(user: UserDto) = act("Removed @${user.username}.") { contacts.remove(user.id) }
    fun block(user: UserDto) = act("Blocked @${user.username}.") { contacts.block(user.id) }

    private fun act(okMessage: String?, call: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            when (val r = call()) {
                is AppResult.Success -> {
                    okMessage?.let { m -> _state.update { it.copy(message = m) } }
                    refresh(silent = true)
                    chat.refreshConversations()
                }
                is AppResult.Failure -> _state.update { it.copy(message = r.error.message) }
            }
        }
    }

    fun openChat(user: UserDto) {
        viewModelScope.launch {
            when (val r = chat.openDirect(user.id)) {
                is AppResult.Success -> _state.update { it.copy(openChatId = r.data) }
                is AppResult.Failure -> _state.update { it.copy(message = r.error.message) }
            }
        }
    }

    fun chatOpened() = _state.update { it.copy(openChatId = null) }
    fun messageShown() = _state.update { it.copy(message = null) }
}
