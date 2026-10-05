package com.martdev.flickq.notification.model

data class RegisterDeviceRequest(
    val token: String,
    val platform: DevicePlatform,
    val appVersion: String,
    val locale: String
)
