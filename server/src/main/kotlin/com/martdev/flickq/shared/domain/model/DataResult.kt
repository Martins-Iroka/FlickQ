package com.martdev.flickq.shared.domain.model

sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    sealed interface Failure : DataResult<Nothing> {
        data class NotFound(val errorMessage: String = "not found") : Failure
        data object UniqueViolation : Failure
        data object ForeignKeyViolation : Failure
        data class Conflict(val errorMessage: String = "conflict") : Failure
        data class UnknownError(val errorMessage: String, val cause: Throwable = Exception()) : Failure
    }
}