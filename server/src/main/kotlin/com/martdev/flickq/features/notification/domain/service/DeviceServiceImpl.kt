package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.notification.model.RegisterDeviceRequest
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.util.getLoggerFactory
import com.martdev.flickq.shared.util.returnValue
import org.koin.core.annotation.Singleton
import kotlin.time.Clock

@Singleton
class DeviceServiceImpl(
    private val devices: DeviceTokenRepository,
    private val outbox: NotificationOutboxRepository,
    private val composer: NotificationComposer
) : DeviceService {

    val log = getLoggerFactory("DeviceService")

    override suspend fun register(
        userId: Long,
        request: RegisterDeviceRequest
    ): DeviceToken {
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
        return saved.returnValue()
    }

    override suspend fun unregister(
        userId: Long,
        token: String
    ) {
        when (val result = devices.invalidateForUser(userId, token)) {
            is DataResult.Success -> {
                if (result.value)
                    outbox.enqueue(composer.unsubscribeFromNewMovies(token, Clock.System.now()))
            }

            is DataResult.Failure -> {
                log.warn("The device with id {} failed to unregister due to {}", userId, result.toString())
            }
        }
    }
}