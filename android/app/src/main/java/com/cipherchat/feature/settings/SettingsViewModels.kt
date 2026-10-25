package com.cipherchat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cipherchat.BuildConfig
import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.network.ApiClient
import com.cipherchat.core.network.BlockDto
import com.cipherchat.core.network.DeviceDto
import com.cipherchat.core.network.UserDto
import com.cipherchat.data.AppSettings
import com.cipherchat.data.AuthRepository
import com.cipherchat.data.ContactsRepository
import com.cipherchat.data.SessionState
import com.cipherchat.data.ThemeMode
import com.cipherchat.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsTabState(val user: UserDto? = null, val prefs: UserPrefs? = null)

class SettingsTabViewModel(private val settings: AppSettings, private val auth: AuthRepository) : ViewModel() {
    val state: StateFlow<SettingsTabState> = combine(auth.session, settings.prefs) { session, prefs ->
        SettingsTabState((session as? SessionState.LoggedIn)?.user, prefs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsTabState())

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { settings.setTheme(mode) } }
    fun signOut() { viewModelScope.launch { auth.logout() } }
}

data class ProfileState(val displayName: String = "", val saving: Boolean = false, val message: String? = null, val error: String? = null, val username: String = "")

class ProfileViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        (auth.session.value as? SessionState.LoggedIn)?.user.let { ProfileState(displayName = it?.displayName.orEmpty(), username = it?.username.orEmpty()) },
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(displayName = v, message = null, error = null) }

    fun save() {
        val name = _state.value.displayName.trim()
        if (name.isEmpty() || name.length > 64) {
            _state.update { it.copy(error = "Display name must be 1–64 characters.") }
            return
        }
        _state.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            when (val r = auth.updateDisplayName(name)) {
                is AppResult.Success -> _state.update { it.copy(saving = false, message = "Saved.") }
                is AppResult.Failure -> _state.update { it.copy(saving = false, error = r.error.message) }
            }
        }
    }
}

data class PrivacyState(
    val prefs: UserPrefs? = null,
    val discoverable: Boolean? = null,
    val blocks: List<BlockDto> = emptyList(),
    val message: String? = null,
)

class PrivacyViewModel(
    private val settings: AppSettings,
    private val auth: AuthRepository,
    private val contacts: ContactsRepository,
) : ViewModel() {
    private val extra = MutableStateFlow(PrivacyState())
    val state: StateFlow<PrivacyState> = combine(settings.prefs, extra) { p, e -> e.copy(prefs = p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyState())

    init {
        viewModelScope.launch {
            (auth.discoverable() as? AppResult.Success)?.let { r -> extra.update { it.copy(discoverable = r.data) } }
            (contacts.blocks() as? AppResult.Success)?.let { r -> extra.update { it.copy(blocks = r.data) } }
        }
    }

    fun setBiometric(on: Boolean) { viewModelScope.launch { settings.setBiometricLock(on) } }
    fun setBlockScreenshots(on: Boolean) { viewModelScope.launch { settings.setBlockScreenshots(on) } }

    fun setDiscoverable(on: Boolean) {
        viewModelScope.launch {
            when (val r = auth.setDiscoverable(on)) {
                is AppResult.Success -> extra.update { it.copy(discoverable = r.data) }
                is AppResult.Failure -> extra.update { it.copy(message = r.error.message) }
            }
        }
    }

    fun unblock(b: BlockDto) {
        viewModelScope.launch {
            when (val r = contacts.unblock(b.id)) {
                is AppResult.Success -> extra.update { it.copy(blocks = it.blocks - b, message = "Unblocked @${b.user.username}.") }
                is AppResult.Failure -> extra.update { it.copy(message = r.error.message) }
            }
        }
    }

    fun messageShown() = extra.update { it.copy(message = null) }
}

data class DevicesState(val loading: Boolean = true, val error: AppError? = null, val devices: List<DeviceDto> = emptyList(), val message: String? = null)

class DevicesViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(DevicesState())
    val state: StateFlow<DevicesState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.devices()) {
                is AppResult.Success -> _state.update { it.copy(loading = false, devices = r.data) }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun revoke(d: DeviceDto) {
        viewModelScope.launch {
            when (val r = auth.revokeDevice(d.id)) {
                is AppResult.Success -> {
                    _state.update { it.copy(message = "Signed out ${d.name}.") }
                    load()
                }
                is AppResult.Failure -> _state.update { it.copy(message = r.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

data class DeleteState(val password: String = "", val confirmed: Boolean = false, val loading: Boolean = false, val error: String? = null)

class DeleteAccountViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(DeleteState())
    val state: StateFlow<DeleteState> = _state.asStateFlow()

    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onConfirmed(v: Boolean) = _state.update { it.copy(confirmed = v) }

    fun delete() {
        val s = _state.value
        if (!s.confirmed || s.password.isEmpty() || s.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // On success AuthRepository wipes local state and the session flips to LoggedOut (navigation follows).
            when (val r = auth.deleteAccount(s.password)) {
                is AppResult.Success -> _state.update { it.copy(loading = false, password = "") }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }
}

object ServerUrlValidator {
    /** Returns an error message, or null when [raw] is acceptable. Release builds require HTTPS. */
    fun validate(raw: String, allowCleartext: Boolean = BuildConfig.DEBUG): String? {
        val url = raw.trim()
        val lower = url.lowercase()
        if (url.isEmpty()) return "Enter a server address."
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return "Address must start with https://"
        if (lower.startsWith("http://") && !allowCleartext) return "Use https:// — plain http:// is only allowed in debug builds."
        val host = url.substringAfter("://").substringBefore("/").substringBefore(":")
        if (host.isBlank() || host.any { it.isWhitespace() }) return "That doesn't look like a valid address."
        return null
    }
}

data class ServerState(val url: String = "", val message: String? = null, val error: String? = null, val checking: Boolean = false)

class ServerViewModel(private val settings: AppSettings, private val api: ApiClient) : ViewModel() {
    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    init { viewModelScope.launch { _state.update { it.copy(url = settings.current().baseUrl) } } }

    fun onUrl(v: String) = _state.update { it.copy(url = v, error = null, message = null) }

    fun save() {
        val url = _state.value.url
        ServerUrlValidator.validate(url)?.let { err -> _state.update { it.copy(error = err) }; return }
        viewModelScope.launch {
            settings.setBaseUrl(ApiClient.normalize(url))
            _state.update { it.copy(message = "Saved. Sign in again if you switched servers.", url = ApiClient.normalize(url)) }
        }
    }
}
