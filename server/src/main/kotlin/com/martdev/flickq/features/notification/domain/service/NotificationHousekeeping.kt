package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.util.getLoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class HousekeepingReport(
    val outboxPurged: Int,
    val deadPurged: Int,
    val tokensPruned: Int,
    val unsubscribesQueued: Int,
)

class NotificationHousekeeping(
    private val outbox: NotificationOutboxRepository,
    private val devices: DeviceTokenRepository,
    private val composer: NotificationComposer,
    private val config: FcmConfig,
    private val clock: Clock = Clock.System,
) {
    private val log = getLoggerFactory("NotificationHousekeeping")

    suspend fun run(): HousekeepingReport {
        val now = clock.now()
        val (pruned, queued) = pruneTokens(now)
        val report = HousekeepingReport(
            outboxPurged = purge(DELIVERED, now - config.retentionSentDays.days),
            deadPurged = purge(DEAD, now - config.retentionDeadDays.days),
            tokensPruned = pruned,
            unsubscribesQueued = queued,
        )
        log.info(
            "event=notification.housekeeping outbox_purged={} dead_purged={} tokens_pruned={} unsubscribes_queued={}",
            report.outboxPurged, report.deadPurged, report.tokensPruned, report.unsubscribesQueued,
        )
        return report
    }

    /** Small chunks keep each DELETE's locks short; MAX_CHUNKS bounds one run (the rest waits for the next hour). */
    private suspend fun purge(statuses: Set<NotificationStatus>, before: Instant): Int {
        var total = 0
        repeat(MAX_CHUNKS) {
            when (val r = outbox.purgeFinished(statuses, before, CHUNK)) {
                is DataResult.Success -> {
                    total += r.value
                    if (r.value < CHUNK) return total
                }

                is DataResult.Failure -> {
                    log.warn(
                        "outbox purge {} failed after {} rows: {}",
                        statuses,
                        total,
                        r::class.simpleName
                    )
                    return total
                }
            }
        }
        return total
    }

    private suspend fun pruneTokens(now: Instant): Pair<Int, Int> {
        var pruned = 0
        var queued = 0
        repeat(MAX_CHUNKS) {
            val result = when (val r = devices.pruneStale(
                staleBefore = now - config.tokenStaleDays.days,
                invalidatedBefore = now - config.tokenInvalidatedDays.days,
                limit = CHUNK,
            )) {
                is DataResult.Success -> r.value
                is DataResult.Failure -> {
                    log.warn("token prune failed after {} rows: {}", pruned, r::class.simpleName)
                    return pruned to queued
                }
            }
            pruned += result.deleted
            // Topic membership belongs to the token. If a deleted "stale" token is in fact alive, it would keep
            // getting movie pushes, so unsubscribe it. Best effort: the delete already committed, and a token
            // unseen for 60 days is almost always dead (FCM answers UNREGISTERED → row SENT "all_tokens_invalid").
            result.activeTokens.forEach { token ->
                val r = outbox.enqueue(composer.unsubscribeFromNewMovies(token, now))
                if ((r as? DataResult.Success)?.value == true) queued++
            }
            if (result.deleted < CHUNK) return pruned to queued
        }
        return pruned to queued
    }

    private companion object {
        const val CHUNK = 1_000
        const val MAX_CHUNKS = 50
        val DELIVERED = setOf(NotificationStatus.SENT, NotificationStatus.EXPIRED)
        val DEAD = setOf(NotificationStatus.DEAD)
    }
}