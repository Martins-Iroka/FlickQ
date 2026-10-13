package com.martdev.flickq.shared.util

import kotlin.uuid.Uuid

/** Stable per-process id: the outbox lease owner, and `service.instance.id` on every metric and span. */
object InstanceId {
    val value: String by lazy {
        System.getenv("FLY_MACHINE_ID") ?: "local-${Uuid.random().toString().take(8)}"
    }
}

