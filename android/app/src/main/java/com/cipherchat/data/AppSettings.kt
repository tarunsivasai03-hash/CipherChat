package com.cipherchat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cipherchat.BuildConfig
import com.cipherchat.core.network.NetworkJson
import com.cipherchat.core.network.UserDto
import com.cipherchat.core.security.TokenCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cipherchat_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class UserPrefs(
    val baseUrl: String,
    val themeMode: ThemeMode,
    val biometricLock: Boolean,
    val blockScreenshots: Boolean,
    val onboardingDone: Boolean,
)

/**
 * Non-sensitive preferences plus the Keystore-encrypted session token.
 * The token is stored only as ciphertext; see [TokenCrypto].
 */
class AppSettings(context: Context, private val crypto: TokenCrypto = TokenCrypto()) {
    private val store = context.applicationContext.dataStore

    private object K {
        val baseUrl = stringPreferencesKey("base_url")
        val theme = stringPreferencesKey("theme")
        val biometric = booleanPreferencesKey("biometric_lock")
        val noScreenshots = booleanPreferencesKey("block_screenshots")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val tokenEnc = stringPreferencesKey("token_enc")
        val userJson = stringPreferencesKey("user_json")
        val deviceId = stringPreferencesKey("device_id")
    }

    val prefs: Flow<UserPrefs> = store.data.map { p ->
        UserPrefs(
            baseUrl = p[K.baseUrl] ?: BuildConfig.DEFAULT_BASE_URL,
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            biometricLock = p[K.biometric] ?: false,
            blockScreenshots = p[K.noScreenshots] ?: false,
            onboardingDone = p[K.onboarding] ?: false,
        )
    }

    suspend fun current(): UserPrefs = prefs.first()

    suspend fun setBaseUrl(url: String) = store.edit { it[K.baseUrl] = url.trim() }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[K.theme] = mode.name }
    suspend fun setBiometricLock(enabled: Boolean) = store.edit { it[K.biometric] = enabled }
    suspend fun setBlockScreenshots(enabled: Boolean) = store.edit { it[K.noScreenshots] = enabled }
    suspend fun setOnboardingDone() = store.edit { it[K.onboarding] = true }

    /** Persist the session. Throws if the Keystore is unavailable, so the caller cannot proceed with an unprotected token. */
    suspend fun saveSession(token: String, user: UserDto, deviceId: Long) {
        val enc = crypto.encrypt(token)
        store.edit {
            it[K.tokenEnc] = enc
            it[K.userJson] = NetworkJson.encodeToString(UserDto.serializer(), user)
            it[K.deviceId] = deviceId.toString()
        }
    }

    suspend fun loadToken(): String? {
        val enc = store.data.first()[K.tokenEnc] ?: return null
        return runCatching { crypto.decrypt(enc) }.getOrNull()
    }

    suspend fun loadUser(): UserDto? =
        store.data.first()[K.userJson]?.let { runCatching { NetworkJson.decodeFromString(UserDto.serializer(), it) }.getOrNull() }

    suspend fun loadDeviceId(): Long? = store.data.first()[K.deviceId]?.toLongOrNull()

    suspend fun saveUser(user: UserDto) = store.edit { it[K.userJson] = NetworkJson.encodeToString(UserDto.serializer(), user) }

    suspend fun clearSession() {
        store.edit {
            it.remove(K.tokenEnc)
            it.remove(K.userJson)
            it.remove(K.deviceId)
        }
        crypto.deleteKey()
    }
}
