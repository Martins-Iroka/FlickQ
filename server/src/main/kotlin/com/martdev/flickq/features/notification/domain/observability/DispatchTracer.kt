package com.martdev.flickq.features.notification.domain.observability

import com.martdev.flickq.features.notification.domain.model.OutboxEntry

interface DispatchTracer {
    /** Runs one dispatch unit (a row, or a coalesced topic group) in a span linked to each row's producer trace. */
    suspend fun <T> span(operation: String, rows: List<OutboxEntry>, block: suspend () -> T): T

    object Noop : DispatchTracer {
        override suspend fun <T> span(operation: String, rows: List<OutboxEntry>, block: suspend () -> T): T = block()
    }
}