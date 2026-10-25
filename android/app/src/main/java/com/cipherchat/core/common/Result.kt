package com.cipherchat.core.common

/** Typed failure used everywhere instead of raw exceptions, so UI can show honest, specific messages. */
data class AppError(
    val kind: Kind,
    val message: String,
    val code: String? = null,
    val httpStatus: Int? = null,
) {
    enum class Kind { NETWORK, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, VALIDATION, SERVER, UNKNOWN }

    /** True when retrying later without changing the request could succeed. */
    val isRetriable: Boolean get() = kind == Kind.NETWORK || kind == Kind.SERVER || kind == Kind.RATE_LIMITED

    /** True only when the server clearly refused the request, so retrying unchanged is pointless. */
    val isDefinitiveRejection: Boolean
        get() = kind == Kind.VALIDATION || kind == Kind.FORBIDDEN || kind == Kind.NOT_FOUND || kind == Kind.CONFLICT
}

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

/** Consistent loading/content/error model for screens that fetch data. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Content<T>(val data: T) : Load<T>
    data class Failed(val error: AppError) : Load<Nothing>
}
