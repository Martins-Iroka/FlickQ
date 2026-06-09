package com.martdev.flickq.core.common

interface Error

sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>

    /**
     * [message] optionally carries the verbatim error message the server returned in its
     * `{"error": ...}` envelope (see the backend `ErrorResponse`). It is populated for 4xx
     * responses and threaded up the chain so the UI can show the server's wording instead of a
     * generic fallback; it is `null` for transport failures, 5xx, and locally produced errors.
     */
    data class Error<out E : com.martdev.flickq.core.common.Error>(
        val error: E,
        val message: String? = null,
    ) : Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>

inline fun <T, E : Error, R> Result<T, E>.map(map: (T) -> R): Result<R, E> {
    return when (this) {
        is Result.Error -> Result.Error(error, message)
        is Result.Success -> Result.Success(map(data))
    }
}

fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> = map { }

inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Error -> this
        is Result.Success -> {
            action(data)
            this
        }
    }
}

inline fun <T, E : Error> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Error -> {
            action(error)
            this
        }
        is Result.Success -> this
    }
}

/**
 * Like [onFailure] but also hands the caller the server-supplied [Result.Error.message] (if any),
 * so display sites can prefer the backend's wording over a generic fallback. Kotlin selects this
 * overload by the lambda's arity (`{ error, message -> }` vs `{ error -> }`).
 */
inline fun <T, E : Error> Result<T, E>.onFailure(action: (E, String?) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Error -> {
            action(error, message)
            this
        }
        is Result.Success -> this
    }
}
