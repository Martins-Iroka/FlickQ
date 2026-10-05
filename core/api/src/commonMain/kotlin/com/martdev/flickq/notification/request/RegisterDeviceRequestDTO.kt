package com.martdev.flickq.notification.request

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequestDTO(
    val token: String,
    val platform: String,
    val appVersion: String,
    val locale: String
)
