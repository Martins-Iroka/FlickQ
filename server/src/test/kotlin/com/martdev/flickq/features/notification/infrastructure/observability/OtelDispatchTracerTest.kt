package com.martdev.flickq.features.notification.infrastructure.observability

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import com.martdev.flickq.features.notification.domain.model.DeliveryResult
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.features.notification.domain.service.PushMessage
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class OtelDispatchTracerTest {

    private val exporter = InMemorySpanExporter.create()
    private val otel = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()
        )
        .build()
    private val tracer = OtelDispatchTracer(otel)

    private val producerA = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
    private val producerB = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"

    @AfterEach
    fun close() = otel.close()

    private fun row(id: Long = 1, traceParent: String? = producerA) = OutboxEntry(
        id, NotificationType.BOOKING_CONFIRMED, NotificationTargetKind.USER, 5, null,
        PushMessage("t", "b"), null, 2, null, traceParent,
    )

    private fun span() = exporter.finishedSpanItems.single()

    @Test
    fun `a dispatch is a new CONSUMER trace linked to the producer`() = runTest {
        tracer.span("fcm.send", listOf(row())) { DeliveryResult.Sent() }

        val span = span()
        val link = span.links.single().spanContext
        assertThat(span.kind).isEqualTo(SpanKind.CONSUMER)
        assertThat(span.parentSpanContext.isValid).isFalse()
        assertThat(link.traceId).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736")
        assertThat(link.spanId).isEqualTo("00f067aa0ba902b7")
        assertThat(span.traceId).isNotEqualTo(link.traceId)
        assertThat(span.attributes.get(AttributeKey.longKey("outbox.id"))).isEqualTo(1L)
        assertThat(span.attributes.get(AttributeKey.longKey("outbox.attempt"))).isEqualTo(2L)
        assertThat(span.attributes.get(AttributeKey.stringKey("outbox.result"))).isEqualTo("sent")
    }

    @Test
    fun `a missing or malformed traceparent gives no link and no failure`() = runTest {
        tracer.span("fcm.send", listOf(row(1, null))) { DeliveryResult.Sent() }
        tracer.span("fcm.send", listOf(row(2, "not-a-traceparent"))) { DeliveryResult.Sent() }

        exporter.finishedSpanItems.forEach { assertThat(it.links).isEmpty() }
    }

    @Test
    fun `a coalesced group links each distinct producer once`() = runTest {
        tracer.span(
            "fcm.topic_subscribe",
            listOf(row(1, producerA), row(2, producerA), row(3, producerB))
        ) { }

        val span = span()
        assertThat(span.links).hasSize(2)
        assertThat(span.attributes.get(AttributeKey.longKey("messaging.batch.message_count"))).isEqualTo(
            3L
        )
        assertThat(span.attributes.get(AttributeKey.longKey("outbox.id"))).isNull()
    }

    @Test
    fun `a retry records its reason`() = runTest {
        tracer.span("fcm.send", listOf(row())) { DeliveryResult.Retry("x", reason = "UNAVAILABLE") }

        assertThat(span().attributes.get(AttributeKey.stringKey("outbox.reason"))).isEqualTo("UNAVAILABLE")
    }

    @Test
    fun `a dead row marks the span as error`() = runTest {
        tracer.span(
            "fcm.send",
            listOf(row())
        ) { DeliveryResult.Dead("PERMISSION_DENIED (403): nope") }

        assertThat(span().status.statusCode).isEqualTo(StatusCode.ERROR)
    }

    @Test
    fun `an exception is recorded, rethrown and the span still ends`() = runTest {
        assertFailsWith<IllegalStateException> {
            tracer.span("fcm.send", listOf(row())) { error("boom") }
        }

        assertThat(span().status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(span().events.single().name).isEqualTo("exception")
    }
}