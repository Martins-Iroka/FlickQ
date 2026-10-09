package com.martdev.flickq.auth.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogoutRequest(
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("device_token")
    val deviceToken: String? = null
)
