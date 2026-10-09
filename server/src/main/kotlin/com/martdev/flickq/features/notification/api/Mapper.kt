package com.martdev.flickq.features.notification.api

import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.notification.model.RegisterDeviceRequest
import com.martdev.flickq.notification.request.RegisterDeviceRequestDTO

fun RegisterDeviceRequestDTO.toRegisterDeviceRequest() = RegisterDeviceRequest(
    token, DevicePlatform.valueOf(platform.uppercase()), appVersion, locale
)