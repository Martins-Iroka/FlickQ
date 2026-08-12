package com.martdev.flickq.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
                ?.value?.toString() ?: "-"
            "call_id=${call.callId ?: "-"} method=${call.request.httpMethod.value} " +
                    "path=${call.request.path()} status=$status " +
                    "duration_ms=${call.processingTimeMillis()}"
        }
    }
}