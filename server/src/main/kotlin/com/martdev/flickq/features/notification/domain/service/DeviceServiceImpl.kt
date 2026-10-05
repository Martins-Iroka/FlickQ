package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.notification.model.RegisterDeviceRequest
import com.martdev.flickq.shared.domain.model.DataResult
import kotlin.time.Clock

class DeviceServiceImpl(
    private val devices: DeviceTokenRepository,
    private val outbox: NotificationOutboxRepository,
    private val composer: NotificationComposer
) : DeviceService {
    override suspend fun register(
        userId: Long,
        request: RegisterDeviceRequest
    ): DataResult<DeviceToken> {
        val saved = devices.upsert(
            userId,
            request.token,
            request.platform,
            request.appVersion,
            request.locale
        )
        if (saved is DataResult.Success) {
            outbox.enqueue(composer.subscribeToNewMovies(request.token, Clock.System.now()))
        }
        return saved
    }

    override suspend fun unregister(
        userId: Long,
        token: String
    ): DataResult<Unit> = when (val result = devices.invalidateForUser(userId, token)) {
        is DataResult.Success -> {
            if (result.value)
                outbox.enqueue(composer.unsubscribeFromNewMovies(token, Clock.System.now()))
            DataResult.Success(Unit)
        }

        is DataResult.Failure -> result
    }
}