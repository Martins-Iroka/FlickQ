package com.martdev.flickq.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.generate

fun Application.configureMonitoring() {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate(8)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
}