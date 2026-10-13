package com.martdev.flickq.features.notification.infrastructure.observability

import com.martdev.flickq.features.notification.domain.model.DeliveryResult
import com.martdev.flickq.features.notification.domain.model.OutboxEntry
import com.martdev.flickq.features.notification.domain.observability.DispatchTracer
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import kotlinx.coroutines.CancellationException

class OtelDispatchTracer(openTelemetry: OpenTelemetry) : DispatchTracer {

    private val tracer = openTelemetry.getTracer("com.martdev.flickq.notification")

    override suspend fun <T> span(operation: String, rows: List<OutboxEntry>, block: suspend () -> T): T {
        val builder = tracer.spanBuilder(operation)
            .setSpanKind(SpanKind.CONSUMER)
            .setNoParent()
            .setAttribute(MESSAGING_SYSTEM, "fcm")
            .setAttribute(BATCH_SIZE, rows.size.toLong())
        rows.firstOrNull()?.let { builder.setAttribute(TYPE, it.type.name) }
        rows.singleOrNull()?.let {
            builder.setAttribute(OUTBOX_ID, it.id)
            builder.setAttribute(ATTEMPT, it.attempts.toLong())
        }
        rows.asSequence()
            .mapNotNull { it.traceParent?.let(::producerContext) }
            .distinct()
            .forEach { builder.addLink(it) }

        // The span is deliberately NOT made current: Context is thread-local, and makeCurrent() across a
        // suspension point leaks it onto whatever runs next on that thread.
        val span = builder.startSpan()
        try {
            return block().also { if (it is DeliveryResult) span.record(it) }
        } catch (e: CancellationException) {
            span.setAttribute(RESULT, "cancelled")
            throw e
        } catch (e: Throwable) {
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e::class.simpleName ?: "error")
            throw e
        } finally {
            span.end()
        }
    }

    private fun Span.record(result: DeliveryResult) {
        when (result) {
            is DeliveryResult.Sent -> {
                setAttribute(RESULT, "sent")
                result.note?.let { setAttribute(NOTE, it) }
            }
            is DeliveryResult.Retry -> {
                setAttribute(RESULT, "retry")
                setAttribute(REASON, result.reason)
            }
            is DeliveryResult.Dead -> {
                setAttribute(RESULT, "dead")
                setStatus(StatusCode.ERROR, result.error.take(200))
            }
            DeliveryResult.Expired -> setAttribute(RESULT, "expired")
        }
    }

    private companion object {
        val MESSAGING_SYSTEM: AttributeKey<String> = AttributeKey.stringKey("messaging.system")
        val BATCH_SIZE: AttributeKey<Long> = AttributeKey.longKey("messaging.batch.message_count")
        val TYPE: AttributeKey<String> = AttributeKey.stringKey("notification.type")
        val OUTBOX_ID: AttributeKey<Long> = AttributeKey.longKey("outbox.id")
        val ATTEMPT: AttributeKey<Long> = AttributeKey.longKey("outbox.attempt")
        val RESULT: AttributeKey<String> = AttributeKey.stringKey("outbox.result")
        val REASON: AttributeKey<String> = AttributeKey.stringKey("outbox.reason")
        val NOTE: AttributeKey<String> = AttributeKey.stringKey("outbox.note")
    }
}

private const val TRACEPARENT = "traceparent"

/** Parses the `00-<trace>-<span>-<flags>` the writer stored. Null for missing/malformed values: no link, never a failure. */
internal fun producerContext(traceParent: String): SpanContext? =
    Span.fromContext(W3CTraceContextPropagator.getInstance().extract(Context.root(), traceParent, TraceParentGetter))
        .spanContext
        .takeIf { it.isValid }

private object TraceParentGetter : TextMapGetter<String> {
    override fun keys(carrier: String): Iterable<String> = listOf(TRACEPARENT)
    override fun get(carrier: String?, key: String): String? = carrier.takeIf { key == TRACEPARENT }
}