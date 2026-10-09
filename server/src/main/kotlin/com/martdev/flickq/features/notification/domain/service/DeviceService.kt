package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.notification.model.RegisterDeviceRequest

interface DeviceService {
    suspend fun register(userId: Long, request: RegisterDeviceRequest): DeviceToken
    suspend fun unregister(userId: Long, token: String): Unit
}