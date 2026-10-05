package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.notification.model.RegisterDeviceRequest
import com.martdev.flickq.shared.domain.model.DataResult

interface DeviceService {
    suspend fun register(userId: Long, request: RegisterDeviceRequest): DataResult<DeviceToken>
    suspend fun unregister(userId: Long, token: String): DataResult<Unit>
}