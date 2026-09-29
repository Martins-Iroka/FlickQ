package com.martdev.flickq.features.notification.infrastructure.fcm

import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.shared.util.getLoggerFactory
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

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
}