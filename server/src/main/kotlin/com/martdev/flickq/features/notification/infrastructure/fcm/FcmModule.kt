package com.martdev.flickq.features.notification.infrastructure.fcm

import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.service.BackoffPolicy
import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.features.notification.domain.service.NotificationDispatcher
import com.martdev.flickq.shared.util.getLoggerFactory
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

val fcmModule = module {
    single { FirebaseProvider(get()) } onClose { it?.close() }
    single(createdAtStart = true) {
        val config = get<FcmConfig>()
        val log = getLoggerFactory("FcmSender")
        if (config.enabled) {
            val firebaseProvider = get<FirebaseProvider>().also {
                it.messaging
            }
            log.info(
                "FCM enabled project={} dryRun={}",
                config.projectId, config.dryRun
            )
            FirebaseFcmSender(firebaseProvider, config)
        } else {
            log.warn("FCM disabled (fcm.enabled=false) - using LoggingFcmSender")
            LoggingFcmSender()
        }
    } bind FcmSender::class
    single {
        val c = get<FcmConfig>()
        BackoffPolicy(c.baseMs.milliseconds, c.capMs.milliseconds, c.maxAttempts)
    }
    single {
        NotificationDispatcher(
            outbox = get(), get(), get(), get(), get(),
            instanceId = System.getenv("FLY_MACHINE_ID") ?: "local-${Uuid.random().toString().take(8)}"
        )
    }
}