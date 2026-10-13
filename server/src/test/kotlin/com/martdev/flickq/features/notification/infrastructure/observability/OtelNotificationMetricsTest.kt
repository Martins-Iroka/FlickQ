package com.martdev.flickq.features.notification.infrastructure.observability

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.martdev.flickq.features.notification.domain.model.OutboxStats
import com.martdev.flickq.notification.model.NotificationType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class OtelNotificationMetricsTest {

    private val reader = InMemoryMetricReader.create()
    private val otel = OpenTelemetrySdk.builder()
        .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build())
        .build()                                                     // not registered globally
    private val time = TestTimeSource()
    private val metrics = OtelNotificationMetrics(otel, time)

    private val type = AttributeKey.stringKey("type")
    private val reason = AttributeKey.stringKey("reason")
    private val operation = AttributeKey.stringKey("operation")
    private val outcome = AttributeKey.stringKey("outcome")
    private val status = AttributeKey.stringKey("status")

    @AfterEach
    fun close() = otel.close()

    private fun collect(name: String) = reader.collectAllMetrics().filter { it.name == name }
    private fun points(name: String) = collect(name).flatMap { it.data.points }
    private fun longPoints(name: String) = collect(name).flatMap { it.longSumData.points }

    @Test
    fun `counters are tagged with the notification type`() {
        metrics.enqueued(NotificationType.MOVIE_ADDED)
        metrics.enqueued(NotificationType.MOVIE_ADDED)
        metrics.sent(NotificationType.BOOKING_CONFIRMED)
        metrics.dead(NotificationType.RESERVATION_HELD)
        metrics.expired(NotificationType.RESERVATION_HELD)

        val enqueued = longPoints("fcm.enqueued").single()
        assertThat(enqueued.value).isEqualTo(2L)
        assertThat(enqueued.attributes.get(type)).isEqualTo("MOVIE_ADDED")
        assertThat(longPoints("fcm.sent").single().attributes.get(type)).isEqualTo("BOOKING_CONFIRMED")
        assertThat(longPoints("fcm.dead").single().attributes.get(type)).isEqualTo("RESERVATION_HELD")
        assertThat(longPoints("fcm.expired").single().value).isEqualTo(1L)
    }

    @Test
    fun `failed is split by reason and long reasons are truncated`() {
        metrics.failed(NotificationType.RESERVATION_HELD, "UNAVAILABLE")
        metrics.failed(NotificationType.RESERVATION_HELD, "QUOTA_EXCEEDED")
        metrics.failed(NotificationType.RESERVATION_HELD, "X".repeat(500))

        assertThat(longPoints("fcm.failed").map { it.attributes.get(reason) })
            .containsExactlyInAnyOrder("UNAVAILABLE", "QUOTA_EXCEEDED", "X".repeat(40))
    }

    @Test
    fun `zero invalidated tokens records nothing`() {
        metrics.tokensInvalidated(0)
        assertThat(points("fcm.tokens_invalidated")).isEmpty()

        metrics.tokensInvalidated(3)
        assertThat(longPoints("fcm.tokens_invalidated").single().value).isEqualTo(3L)
    }

    @Test
    fun `send duration is a histogram in seconds tagged by operation and outcome`() {
        metrics.sendDuration("tokens", "partial", 250.milliseconds)

        val metric = collect("fcm.send.duration").single()
        val point = metric.histogramData.points.single()
        assertThat(metric.unit).isEqualTo("s")
        assertThat(point.count).isEqualTo(1L)
        assertThat(point.sum).isEqualTo(0.25)
        assertThat(point.attributes.get(operation)).isEqualTo("tokens")
        assertThat(point.attributes.get(outcome)).isEqualTo("partial")
    }

    @Test
    fun `lag and depth report the latest sample`() {
        metrics.outboxStats(OutboxStats(lagSeconds = 42.0, pending = 7, inFlight = 2, dead = 1))

        assertThat(collect("fcm.outbox.lag").single().doubleGaugeData.points.single().value).isEqualTo(42.0)
        assertThat(longPoints("fcm.outbox.depth").associate { it.attributes.get(status) to it.value })
            .isEqualTo(mapOf("PENDING" to 7L, "IN_FLIGHT" to 2L, "DEAD" to 1L))
    }

    @Test
    fun `no sample yet reports nothing`() {
        assertThat(points("fcm.outbox.lag")).isEmpty()
        assertThat(points("fcm.outbox.depth")).isEmpty()
    }

    @Test
    fun `a stale sample is withheld so an absent-data alert can fire`() {
        metrics.outboxStats(OutboxStats(0.0, 0, 0, 0))
        time += 3.minutes

        assertThat(points("fcm.outbox.lag")).isEmpty()
    }
}