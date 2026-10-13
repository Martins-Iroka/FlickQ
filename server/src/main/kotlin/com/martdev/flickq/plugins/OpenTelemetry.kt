package com.martdev.flickq.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.api.config.IncludeExclude
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.ktor.ext.inject
import kotlin.time.Clock

fun Application.configureOpenTelemetry() {
    val openTele by inject<OpenTelemetry>()

    install(KtorServerTelemetry) {
        setOpenTelemetry(openTele)

        knownMethods(HttpMethod.DefaultMethods)

        val ie = IncludeExclude.builder()
        requestHeaders(ie.setIncluded(HttpHeaders.UserAgent, HttpHeaders.XRequestId).build())
        responseHeaders(ie.setIncluded(HttpHeaders.ContentType, HttpHeaders.XRequestId).build())

        spanKindExtractor {
            if (httpMethod == HttpMethod.Post) {
                SpanKind.PRODUCER
            } else SpanKind.CLIENT
        }

        attributesExtractor {
            onStart {
                attributes.put("start-time", Clock.System.now().toLocalDateTime(TimeZone.UTC).toString())
            }

            onEnd {
                attributes.put("end-time", Clock.System.now().toLocalDateTime(TimeZone.UTC).toString())
            }
        }
    }
}