package com.martdev.flickq.auth.request


@Serializable
data class ResendOTPRequest(
    val email: String
)
