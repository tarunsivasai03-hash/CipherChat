package com.cipherchat.core.network

import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

val NetworkJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Where to find the current server URL and bearer token (both can change while the app runs). */
interface ConnectionConfig {
    val baseUrl: String
    val token: String?
}

/**
 * Builds Retrofit lazily and rebuilds it if the user changes the server URL.
 * Tokens are attached by an interceptor and never logged (no body/header logging is enabled).
 */
class ApiClient(
    private val config: ConnectionConfig,
    private val okHttpBase: OkHttpClient = OkHttpClient(),
) {
    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a request that carried a token was rejected with 401 (session expired or revoked). */
    val unauthorized: SharedFlow<Unit> = _unauthorized.asSharedFlow()

    private var cachedUrl: String? = null
    private var cachedService: ApiService? = null

    private val authInterceptor = Interceptor { chain ->
        val token = config.token
        val request = if (token != null) chain.request().newBuilder().header("Authorization", "Bearer $token").build() else chain.request()
        val response: Response = chain.proceed(request)
        if (response.code == 401 && token != null) _unauthorized.tryEmit(Unit)
        response
    }

    val okHttp: OkHttpClient = okHttpBase.newBuilder()
        .addInterceptor(authInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun service(): ApiService {
        val url = normalize(config.baseUrl)
        val existing = cachedService
        if (existing != null && url == cachedUrl) return existing
        val created = Retrofit.Builder()
            .baseUrl(url)
            .client(okHttp)
            .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
        cachedUrl = url
        cachedService = created
        return created
    }

    companion object {
        fun normalize(url: String): String = url.trim().let { if (it.endsWith("/")) it else "$it/" }
    }
}

/** Runs an API call and converts every failure mode into an [AppError]. Never throws except on cancellation. */
suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    AppResult.Failure(mapHttpError(e))
} catch (e: IOException) {
    AppResult.Failure(AppError(AppError.Kind.NETWORK, "Can't reach the server. Check your connection and server address."))
} catch (e: kotlinx.serialization.SerializationException) {
    // A response we can't read. For writes the server may still have acted, so this is treated as retriable.
    AppResult.Failure(AppError(AppError.Kind.SERVER, "Unexpected response from the server."))
} catch (e: IllegalArgumentException) {
    AppResult.Failure(AppError(AppError.Kind.UNKNOWN, "The server address is invalid."))
} catch (e: Exception) {
    AppResult.Failure(AppError(AppError.Kind.UNKNOWN, "Something went wrong."))
}

fun mapHttpError(e: HttpException): AppError {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    val parsed = body?.let { runCatching { NetworkJson.decodeFromString(ErrorBody.serializer(), it) }.getOrNull() }
    val code = parsed?.error?.code
    val serverMessage = parsed?.error?.message
    val status = e.code()
    val kind = when (status) {
        401 -> AppError.Kind.UNAUTHORIZED
        403 -> AppError.Kind.FORBIDDEN
        404 -> AppError.Kind.NOT_FOUND
        409 -> AppError.Kind.CONFLICT
        429 -> AppError.Kind.RATE_LIMITED
        in 400..499 -> AppError.Kind.VALIDATION
        in 500..599 -> AppError.Kind.SERVER
        else -> AppError.Kind.UNKNOWN
    }
    val message = when (kind) {
        AppError.Kind.RATE_LIMITED -> "Too many attempts. Please wait a moment and try again."
        AppError.Kind.SERVER -> "The server had a problem. Try again shortly."
        AppError.Kind.UNAUTHORIZED -> if (code == "invalid_credentials") "Invalid username or password." else "Your session has expired. Please sign in again."
        else -> serverMessage?.replaceFirstChar { it.uppercase() } ?: "Request failed ($status)."
    }
    return AppError(kind, message, code, status)
}
