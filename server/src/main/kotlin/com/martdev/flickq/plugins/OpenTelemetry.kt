package com.martdev.flickq.plugins

import com.martdev.flickq.config.OpenTeleConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.instrumentation.api.config.IncludeExclude
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ServiceAttributes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.ktor.ext.inject
import kotlin.time.Clock

fun Application.configureOpenTelemetry() {
    val openTeleConfig by inject<OpenTeleConfig>()

    val openTele = configureOpenTelemetry(openTeleConfig)

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

private fun configureOpenTelemetry(config: OpenTeleConfig): OpenTelemetry {
    val resource = Resource.builder()
        .put(ServiceAttributes.SERVICE_NAME, "flickQ-server")
        .build()

    val endpoint = config.endpoint

    val spanExporter = OtlpHttpSpanExporter.builder()
        .setEndpoint("$endpoint/v1/traces")
        .addHeader("api-key", config.apiKey)
        .addHeader("Authorization", config.authKey)
        .build()

    val logExporter = OtlpHttpLogRecordExporter.builder()
        .setEndpoint("$endpoint/v1/logs")
        .addHeader("api-key", config.apiKey)
        .addHeader("Authorization", config.authKey)
        .build()

    val tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
        .build()

    val loggerProvider = SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
        .build()

    val openTele = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setLoggerProvider(loggerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal()

    OpenTelemetryAppender.install(openTele)

    return openTele
}