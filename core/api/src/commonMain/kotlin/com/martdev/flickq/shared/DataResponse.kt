package com.martdev.flickq.shared

import kotlinx.serialization.Serializable

/**
 * Success envelope every FlickQ backend endpoint wraps its payload in: `{ "data": ... }`.
 * Real data sources unwrap `.data`; error bodies use [ErrorResponse] instead.
 */
@Serializable
data class DataResponse<T>(
    val data: T
)

/** Error envelope returned by the backend: `{ "error": "..." }`. */
@Serializable
data class ErrorResponse(
    val error: String
)
