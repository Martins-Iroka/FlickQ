package com.martdev.flickq.features.notification.infrastructure.fcm

import com.martdev.flickq.config.DatabaseConfig
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.service.BackoffPolicy
import com.martdev.flickq.features.notification.domain.service.FcmSender
import com.martdev.flickq.features.notification.domain.service.NotificationDispatcher
import com.martdev.flickq.features.notification.domain.service.TokenBucket
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
                "FCM enabled project={} dryRun={} ratePerSecond={}",
                config.projectId, config.dryRun, config.ratePerSecond
            )
            RateLimitedFcmSender(
                FirebaseFcmSender(firebaseProvider, config),
                TokenBucket(config.ratePerSecond.toDouble())

            )
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
            outbox = get(), get(), get(),
            clampToPool(get(),
                getOrNull<DatabaseConfig>()?.maxOpenCon),
            get(),
            instanceId = System.getenv("FLY_MACHINE_ID") ?: "local-${
                Uuid.random().toString().take(8)
            }"
        )
    }
}

/** Each in-flight row holds a DB connection while it settles; leave headroom so API requests are never starved. */
internal fun clampToPool(config: FcmConfig, poolSize: Int?): FcmConfig {
    if (poolSize == null) return config
    val max = (poolSize - POOL_HEADROOM).coerceAtLeast(1)
    if (config.concurrency <= max) return config
    getLoggerFactory("FcmModule").warn(
        "fcm.worker.concurrency={} exceeds DB pool {} minus headroom {}; using {}",
        config.concurrency, poolSize, POOL_HEADROOM, max
    )
    return config.copy(concurrency = max)
}

private const val POOL_HEADROOM = 4