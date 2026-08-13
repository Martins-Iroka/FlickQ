package com.martdev.flickq.plugins

import com.martdev.flickq.shared.util.getLoggerFactory
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path

fun Application.configureCallLogging() {
    install(CallLogging) {
        logger = getLoggerFactory("CallLogging")
        format { call ->
            val status = call.response.status()
                ?.value?.toString() ?: "-"
            "call_id=${call.callId ?: "-"} method=${call.request.httpMethod.value} " +
                    "path=${call.request.path()} status=$status " +
                    "duration_ms=${call.processingTimeMillis()}"
        }
    }
}