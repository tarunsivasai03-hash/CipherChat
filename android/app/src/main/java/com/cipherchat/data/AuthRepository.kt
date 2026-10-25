package com.cipherchat.data

import android.os.Build
import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.common.map
import com.cipherchat.core.common.onSuccess
import com.cipherchat.core.database.AppDatabase
import com.cipherchat.core.network.ApiClient
import com.cipherchat.core.network.ConnectionConfig
import com.cipherchat.core.network.DeleteAccountRequest
import com.cipherchat.core.network.DeviceDto
import com.cipherchat.core.network.LoginRequest
import com.cipherchat.core.network.RegisterRequest
import com.cipherchat.core.network.UpdateProfileRequest
import com.cipherchat.core.network.UpdateSettingsRequest
import com.cipherchat.core.network.UserDto
import com.cipherchat.core.network.apiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: UserDto) : SessionState
}

/** The sign-in surface ViewModels depend on (lets tests substitute a fake). */
interface Authenticator {
    suspend fun login(username: String, password: String): AppResult<UserDto>
    suspend fun register(username: String, password: String, displayName: String?): AppResult<UserDto>
}

/** Mutable connection settings shared by the HTTP client and the socket. */
class RuntimeConnectionConfig(@Volatile override var baseUrl: String, @Volatile override var token: String? = null) : ConnectionConfig

class AuthRepository(
    private val settings: AppSettings,
    private val api: ApiClient,
    private val config: RuntimeConnectionConfig,
    private val database: AppDatabase,
    private val deviceName: String = Build.MODEL ?: "Android device",
) : Authenticator {
    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    val currentUserId: Long? get() = (session.value as? SessionState.LoggedIn)?.user?.id

    /** Called once at startup. Shows the cached user immediately (works offline), then verifies with the server. */
    suspend fun restore() {
        config.baseUrl = settings.current().baseUrl
        val token = settings.loadToken()
        val cached = settings.loadUser()
        if (token == null || cached == null) {
            _session.value = SessionState.LoggedOut
            return
        }
        config.token = token
        _session.value = SessionState.LoggedIn(cached)
        when (val r = apiCall { api.service().me() }) {
            is AppResult.Success -> {
                settings.saveUser(r.data.user)
                _session.value = SessionState.LoggedIn(r.data.user)
            }
            is AppResult.Failure -> if (r.error.kind == AppError.Kind.UNAUTHORIZED) clearLocalSession()
        }
    }

    override suspend fun register(username: String, password: String, displayName: String?): AppResult<UserDto> {
        config.baseUrl = settings.current().baseUrl
        val r = apiCall {
            api.service().register(RegisterRequest(username.trim(), password, displayName?.trim()?.ifEmpty { null }, deviceName))
        }
        return finishAuth(r)
    }

    override suspend fun login(username: String, password: String): AppResult<UserDto> {
        config.baseUrl = settings.current().baseUrl
        return finishAuth(apiCall { api.service().login(LoginRequest(username.trim(), password, deviceName)) })
    }

    private suspend fun finishAuth(r: AppResult<com.cipherchat.core.network.AuthResponse>): AppResult<UserDto> = when (r) {
        is AppResult.Failure -> if (r.error.kind == AppError.Kind.NETWORK) {
            // Say which address failed: on a real phone the emulator default (10.0.2.2) is the usual culprit.
            AppResult.Failure(
                r.error.copy(
                    message = "${r.error.message}\nTried: ${config.baseUrl}\nOn a phone, set your computer's address under Server settings.",
                ),
            )
        } else r
        is AppResult.Success -> try {
            settings.saveSession(r.data.token, r.data.user, r.data.deviceId)
            config.token = r.data.token
            _session.value = SessionState.LoggedIn(r.data.user)
            AppResult.Success(r.data.user)
        } catch (e: Exception) {
            // Keystore unavailable: refuse to continue rather than keep the token unprotected.
            AppResult.Failure(AppError(AppError.Kind.UNKNOWN, "Secure storage is unavailable on this device, so sign-in was cancelled."))
        }
    }

    /** Revokes the session on the server (best effort) and wipes local state. */
    suspend fun logout() {
        apiCall { api.service().logout() }
        clearLocalSession()
    }

    /** Wipes token, cached user and the local message cache. Safe to call repeatedly. */
    suspend fun clearLocalSession() {
        config.token = null
        settings.clearSession()
        withContext(Dispatchers.IO) { database.clearAllTables() }
        _session.value = SessionState.LoggedOut
    }

    suspend fun updateDisplayName(name: String): AppResult<UserDto> =
        apiCall { api.service().updateMe(UpdateProfileRequest(name.trim())) }.map { it.user }.also { r ->
            r.onSuccess { u ->
                settings.saveUser(u)
                _session.value = SessionState.LoggedIn(u)
            }
        }

    suspend fun deleteAccount(password: String): AppResult<Unit> {
        val r = apiCall { api.service().deleteAccount(DeleteAccountRequest(password)) }
        if (r is AppResult.Success) clearLocalSession()
        return r
    }

    suspend fun devices(): AppResult<List<DeviceDto>> = apiCall { api.service().devices().devices }
    suspend fun revokeDevice(id: Long): AppResult<DeviceDto> = apiCall { api.service().revokeDevice(id).device }
    suspend fun discoverable(): AppResult<Boolean> = apiCall { api.service().settings().settings.discoverable }
    suspend fun setDiscoverable(value: Boolean): AppResult<Boolean> =
        apiCall { api.service().updateSettings(UpdateSettingsRequest(value)).settings.discoverable }

    suspend fun serverSupportsE2e(): Boolean = (apiCall { api.service().health() } as? AppResult.Success)?.data?.e2eEnabled == true
}
