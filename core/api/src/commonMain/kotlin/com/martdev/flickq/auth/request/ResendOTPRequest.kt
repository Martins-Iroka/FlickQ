package com.martdev.flickq.auth.request

import kotlinx.serialization.Serializable


@Serializable
data class ResendOTPRequest(
    val email: String
)
