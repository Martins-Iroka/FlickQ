package com.martdev.flickq.features.notification.infrastructure.observability

import com.martdev.flickq.features.notification.domain.model.OutboxStats
import com.martdev.flickq.features.notification.domain.observability.NotificationMetrics
import com.martdev.flickq.notification.model.NotificationType
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class OtelNotificationMetrics(
    openTelemetry: OpenTelemetry,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : NotificationMetrics {

    private val meter = openTelemetry.getMeter(SCOPE)

    private val enqueuedCounter =
        counter("fcm.enqueued", "Rows committed to the notification outbox")
    private val sentCounter =
        counter("fcm.sent", "Rows settled as SENT (incl. no_devices / all_tokens_invalid)")
    private val failedCounter =
        counter("fcm.failed", "Transient delivery attempts, rescheduled with backoff")
    private val deadCounter =
        counter("fcm.dead", "Rows that gave up: fatal error or attempts exhausted")
    private val expiredCounter =
        counter("fcm.expired", "Rows dropped because expires_at passed before delivery")
    private val invalidatedCounter = meter.counterBuilder("fcm.tokens_invalidated")
        .setUnit("{token}")
        .setDescription("Device tokens FCM reported as dead")
        .build()
    private val sendLatency = meter.histogramBuilder("fcm.send.duration")
        .setUnit("s")
        .setDescription("Latency of one FCM API call")
        .setExplicitBucketBoundariesAdvice(LATENCY_BUCKETS)
        .build()

    @Volatile
    private var latest: Pair<OutboxStats, TimeMark>? = null

    init {
        // Named `fcm.outbox.lag` with unit "s": Prometheus-style backends export it as `fcm_outbox_lag_seconds`.
        // Naming it `…lag_seconds` would end up as `fcm_outbox_lag_seconds_seconds`.
        meter.gaugeBuilder("fcm.outbox.lag")
            .setUnit("s")
            .setDescription("Age of the oldest due PENDING outbox row")
            .buildWithCallback { m -> fresh()?.let { m.record(it.lagSeconds) } }

        meter.upDownCounterBuilder("fcm.outbox.depth")
            .setUnit("{row}")
            .setDescription("Outbox rows by status")
            .buildWithCallback { m ->
                fresh()?.let {
                    m.record(it.pending, STATUS_PENDING)
                    m.record(it.inFlight, STATUS_IN_FLIGHT)
                    m.record(it.dead, STATUS_DEAD)
                }
            }
    }

    override fun enqueued(type: NotificationType) =
        enqueuedCounter.add(1, TYPE_ATTRS.getValue(type))

    override fun sent(type: NotificationType) = sentCounter.add(1, TYPE_ATTRS.getValue(type))
    override fun dead(type: NotificationType) = deadCounter.add(1, TYPE_ATTRS.getValue(type))
    override fun expired(type: NotificationType) = expiredCounter.add(1, TYPE_ATTRS.getValue(type))

    override fun failed(type: NotificationType, reason: String) =
        failedCounter.add(1, Attributes.of(TYPE, type.name, REASON, reason.take(MAX_REASON)))

    override fun tokensInvalidated(count: Int) {
        if (count > 0) invalidatedCounter.add(count.toLong())
    }

    override fun sendDuration(operation: String, outcome: String, duration: Duration) =
        sendLatency.record(
            duration.toDouble(DurationUnit.SECONDS),
            Attributes.of(OPERATION, operation, OUTCOME, outcome)
        )

    override fun outboxStats(stats: OutboxStats) {
        latest = stats to timeSource.markNow()
    }

    // If the sampler gets stuck, the gauge must not freeze at a healthy-looking value. Reporting no data
    // lets an "absent" alert fire instead.
    private fun fresh(): OutboxStats? =
        latest?.takeIf { it.second.elapsedNow() < STALE_AFTER }?.first

    private fun counter(name: String, description: String) =
        meter.counterBuilder(name).setUnit("{notification}").setDescription(description).build()

    private companion object {
        const val SCOPE = "com.martdev.flickq.notification"
        const val MAX_REASON = 40
        val STALE_AFTER = 2.minutes
        val TYPE: AttributeKey<String> = AttributeKey.stringKey("type")
        val REASON: AttributeKey<String> = AttributeKey.stringKey("reason")
        val OPERATION: AttributeKey<String> = AttributeKey.stringKey("operation")
        val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("outcome")
        val STATUS: AttributeKey<String> = AttributeKey.stringKey("status")
        val STATUS_PENDING: Attributes = Attributes.of(STATUS, "PENDING")
        val STATUS_IN_FLIGHT: Attributes = Attributes.of(STATUS, "IN_FLIGHT")
        val STATUS_DEAD: Attributes = Attributes.of(STATUS, "DEAD")
        val TYPE_ATTRS: Map<NotificationType, Attributes> =
            NotificationType.entries.associateWith { Attributes.of(TYPE, it.name) }
        val LATENCY_BUCKETS = listOf(0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0)
    }
}