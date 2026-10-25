package com.cipherchat.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cipherchat.core.common.AppResult
import com.cipherchat.data.Authenticator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Client-side checks mirror the server rules so users get instant feedback; the server remains authoritative. */
object AuthValidation {
    private val USERNAME = Regex("^[A-Za-z0-9_.]{3,32}$")
    const val MIN_PASSWORD = 10

    fun username(value: String): String? = when {
        value.isBlank() -> "Enter a username."
        !USERNAME.matches(value.trim()) -> "3–32 characters: letters, digits, _ or ."
        else -> null
    }

    fun password(value: String, username: String): String? = when {
        value.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters."
        value.length > 128 -> "Use at most 128 characters."
        value.equals(username, ignoreCase = true) -> "Password must differ from the username."
        else -> null
    }
}

data class AuthFormState(
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val confirm: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
)

class LoginViewModel(private val auth: Authenticator) : ViewModel() {
    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

    fun onUsername(v: String) = _state.update { it.copy(username = v, error = null, usernameError = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        if (s.username.isBlank() || s.password.isEmpty()) {
            _state.update { it.copy(error = "Enter your username and password.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.login(s.username, s.password)) {
                is AppResult.Success -> _state.update { it.copy(loading = false, password = "") }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }
}

class RegisterViewModel(private val auth: Authenticator) : ViewModel() {
    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

    fun onUsername(v: String) = _state.update { it.copy(username = v, usernameError = null, error = null) }
    fun onDisplayName(v: String) = _state.update { it.copy(displayName = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, passwordError = null, error = null) }
    fun onConfirm(v: String) = _state.update { it.copy(confirm = v, confirmError = null, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val u = AuthValidation.username(s.username)
        val p = AuthValidation.password(s.password, s.username)
        val c = if (s.password != s.confirm) "Passwords don't match." else null
        if (u != null || p != null || c != null) {
            _state.update { it.copy(usernameError = u, passwordError = p, confirmError = c) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.register(s.username, s.password, s.displayName)) {
                is AppResult.Success -> _state.update { it.copy(loading = false, password = "", confirm = "") }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }
}
