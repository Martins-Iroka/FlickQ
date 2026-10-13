package com.martdev.flickq.features.notification.api

import com.martdev.flickq.features.notification.domain.model.OutboxRecord
import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.notification.model.RegisterDeviceRequest
import com.martdev.flickq.notification.request.RegisterDeviceRequestDTO
import com.martdev.flickq.notification.response.OutboxEntryDTO

fun RegisterDeviceRequestDTO.toRegisterDeviceRequest() = RegisterDeviceRequest(
    token, DevicePlatform.valueOf(platform.uppercase()), appVersion, locale
)

fun OutboxRecord.toDTO() = OutboxEntryDTO(
    id = id,
    type = type.name,
    targetKind = targetKind.name,
    status = status.name,
    attempts = attempts,
    topic = topic,
    userId = userId,
    title = title,
    nextAttemptAt = nextAttemptAt,
    expiresAt = expiresAt,
    lastError = lastError,
    createdAt = createdAt,
    sentAt = sentAt,
)