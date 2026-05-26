package com.martdev.flickq.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String
)