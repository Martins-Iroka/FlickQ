package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.model.DeliveryResult
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.util.getLoggerFactory
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NotificationDispatcher(
    private val outbox: NotificationOutboxRepository,
    private val devices: DeviceTokenRepository,
    private val sender: FcmSender,
    private val config: FcmConfig,
    private val backoff: BackoffPolicy,
    private val instanceId: String,
    private val clock: Clock = Clock.System
) {
    private val log = getLoggerFactory("NotificationDispatcher")

    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            val pause = remainingPause()
            if (pause > Duration.ZERO) {
                delay(pause)
                continue
            }

            val claimed = try {
                runOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("dispatch iteration failed", e)
                0
            }
            if (claimed < config.batchSize) delay(config.pollMs.milliseconds)
        }
    }

    suspend fun runOnce(): Int {
        val claimed = when (val r = outbox.claimBatch(instanceId, config.batchSize, LEASE)) {
            is DataResult.Success -> r.value
            is DataResult.Failure -> {
                log.error("outbox claim failed: {}", r::class.simpleName)
                return 0
            }
        }
        if (claimed.isEmpty()) return 0
        record(process(claimed))
        return claimed.size
    }

    private suspend fun process(entries: List<OutboxEntry>): BatchStats = supervisorScope {
        val stats = BatchStats()
        val gate = Semaphore(config.concurrency) // keep below the Hikari pool size so the API isn't starved
        val (topicOps, sends) = entries.partition {
            it.type == NotificationType.TOPIC_SUBSCRIBE || it.type == NotificationType.TOPIC_UNSUBSCRIBE
        }
        sends.forEach { entry ->
            launch {
                gate.withPermit {
                    settle(entry, guarded {
                        deliver(entry)
                    }, stats)
                }
            }
        }

        topicOps.groupBy { it.type to it.topic!! }.forEach { (key, rows) ->
            launch { gate.withPermit {
                topicGroup(key.first, key.second, rows, stats)
            } }
        }
        stats
    }

    private suspend fun deliver(entry: OutboxEntry): DeliveryResult {
        val now = clock.now()
        if (entry.expiresAt != null && entry.expiresAt <= now) return DeliveryResult.Expired

        val message = entry.expiresAt?.let {
            entry.push.copy(ttl = it - now)
        } ?: entry.push

        return when (entry.targetKind) {
            NotificationTargetKind.TOPIC -> when(val o = sender.sendToTopic(entry.topic!!, message)) {
                is SendOutcome.Success -> DeliveryResult.Sent()
                is SendOutcome.Failure -> resolve(listOf("" to o), targetsToken = false)
            }
            NotificationTargetKind.USER -> sendToUser(entry, message)
            NotificationTargetKind.TOKENS -> resolve(sender.sendToTokens(
                entry.pendingTokens.orEmpty(), message
            ).results)
        }
    }

    private suspend fun sendToUser(entry: OutboxEntry, message: PushMessage): DeliveryResult {
        val tokens = entry.pendingTokens ?: when (val r = devices.activeTokensFor(entry.userId!!)) {
            is DataResult.Success -> r.value
            is DataResult.Failure -> return DeliveryResult.Retry("device lookup failed: ${r::class.simpleName}")
        }
        if (tokens.isEmpty()) return DeliveryResult.Sent("no_devices")

        return resolve(sender.sendToTokens(tokens, message).results)
    }

    private suspend fun topicGroup(
        type: NotificationType, topic: String, rows: List<OutboxEntry>, stats: BatchStats
    ) {
        val tokens = rows.flatMap { it.pendingTokens.orEmpty() }.distinct()
        val outcome = try {
            if (type == NotificationType.TOPIC_SUBSCRIBE) {
                sender.subscribeToTopic(tokens, topic)
            } else {
                sender.unsubscribeFromTopic(tokens, topic)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            rows.forEach {
                settle(it, DeliveryResult.Retry("topic call failed: ${e::class.simpleName}"), stats)
            }
            return
        }
        val byToken = outcome.results.toMap()
        rows.forEach { row ->
            val mine = row.pendingTokens.orEmpty().mapNotNull { t ->
                byToken[t]?.let { t to it }
            }
            settle(row, resolve(mine), stats)
        }
    }

    // ---- turning FCM answers into a row outcome -------------------------------------------------
    private suspend fun resolve(
        results: List<Pair<String, SendOutcome>>, targetsToken: Boolean = true
    ): DeliveryResult {
        val invalid = mutableListOf<String>()
        val retry = mutableListOf<String>()
        var ok = 0
        var firstTransient: FcmError? = null
        var fatal: FcmError? = null
        var retryAfter: Duration? = null
        var rateLimited = false

        for ((token, outcome) in results) when (outcome) {
            is SendOutcome.Failure -> {
                val error = outcome.error
                when (FcmErrorClassifier.classify(error, targetsToken)) {
                    FcmErrorClass.TRANSIENT -> {
                        retry += token
                        firstTransient = firstTransient ?: error
                    }

                    FcmErrorClass.INVALID_TOKEN -> invalid += token
                    FcmErrorClass.CONFIG -> {
                        log.error("FCM config error (check the APNS key/cert): {}", error.message)
                    }

                    FcmErrorClass.FATAL -> fatal = fatal ?: error
                }
                error.retryAfter?.let {
                    retryAfter = maxOf(retryAfter ?: Duration.ZERO, it)
                }
                if (error.code == "QUOTA_EXCEEDED" || error.httpStatus == 429) rateLimited = true
            }

            is SendOutcome.Success -> ok++
        }
        if (invalid.isNotEmpty()) devices.invalidate(invalid)

        return when {
            retry.isNotEmpty() -> {
                DeliveryResult.Retry(
                    firstTransient.describe(),
                    retryAfter, retry.takeIf { targetsToken }, rateLimited
                )
            }

            ok == 0 && fatal != null -> DeliveryResult.Dead(fatal.describe())
            ok == 0 && invalid.isNotEmpty() -> DeliveryResult.Sent("all_tokens_invalid")
            else -> DeliveryResult.Sent()
        }
    }

    private suspend fun settle(entry: OutboxEntry, result: DeliveryResult, stats: BatchStats) {
        stats.total.incrementAndGet()
        val applied = when (result) {
            is DeliveryResult.Sent -> outbox.markSent(entry.id, instanceId)
            DeliveryResult.Expired -> outbox.markExpired(entry.id, instanceId)
            is DeliveryResult.Dead -> {
                log.error("outbox {} DEAD: {}", entry.id, result.error)
                outbox.markDead(entry.id, instanceId, result.error)
            }

            is DeliveryResult.Retry -> {
                stats.transient.incrementAndGet()
                if (result.rateLimited) stats.rateLimited.incrementAndGet()

                if (backoff.isExhausted(entry.attempts)) {
                    log.error(
                        "outbox {} DEAD after {} attempts: {}",
                        entry.id,
                        entry.attempts,
                        result.error
                    )
                    outbox.markDead(
                        entry.id,
                        instanceId,
                        "gave up after ${entry.attempts} attempts: ${result.error}"
                    )
                } else {
                    val next = clock.now() + backoff.delayFor(entry.attempts, result.retryAfter)
                    outbox.markRetry(
                        entry.id,
                        instanceId,
                        next,
                        result.error,
                        result.remainingTokens
                    )
                }
            }
        }
        if ((applied as? DataResult.Success)?.value == false) {
            log.warn(
                "outbox {} lease lost before it could be settled; another worker owns it now",
                entry.id
            )
        }
    }

    /** One row's unexpected failure must not touch its siblings or kill the loop. */
    private suspend fun guarded(block: suspend () -> DeliveryResult): DeliveryResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        DeliveryResult.Dead("invalid message: ${e.message}".take(500))
    } catch (e: Exception) {
        DeliveryResult.Retry("unexpected ${e::class.simpleName}: ${e.message}".take(500))
    }

    private fun FcmError?.describe() = this?.let {
        "$code${httpStatus?.let { s -> " ($s)" } ?: ""}: $message".take(500)
    } ?: "unknown"

    // ---- global circuit breaker (per instance) ----------------------------------------------------
    // Per-row backoff alone doesn't stop a fleet hammering FCM during an outage; this pauses CLAIMING.
    private var trips = 0

    @Volatile
    private var pausedUtil = Instant.fromEpochSeconds(0)

    internal fun remainingPause() = (pausedUtil - clock.now()).coerceAtLeast(Duration.ZERO)

    private fun record(stats: BatchStats) {
        val total = stats.total.get()
        val tripped =
            stats.rateLimited.get() > 0 || (total >= MIN_SAMPLE && stats.transient.get() * 2 > total)
        if (tripped) {
            trips++
            pausedUtil = clock.now() + backoff.delayFor(trips)
            log.warn("FCM circuit breaker open for {} (trip {})", remainingPause(), trips)
        } else {
            trips = 0
        }
    }

    private class BatchStats {
        val total = AtomicInteger()
        val transient = AtomicInteger()
        val rateLimited = AtomicInteger()
    }

    private companion object {
        val LEASE = 60.seconds
        const val MIN_SAMPLE = 5
    }
}