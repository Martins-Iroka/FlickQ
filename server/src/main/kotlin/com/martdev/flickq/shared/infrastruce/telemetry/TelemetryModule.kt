package com.martdev.flickq.shared.infrastruce.telemetry

import com.martdev.flickq.config.OpenTeleConfig
import com.martdev.flickq.shared.util.InstanceId
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ServiceAttributes
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.time.Duration
import java.util.concurrent.TimeUnit

val telemetryModule = module {
    // One SDK per process. onClose flushes the last span/metric/log batch when Koin stops (redeploy).
    single<OpenTelemetry> { buildOpenTelemetry(get()) } onClose {
        (it as? OpenTelemetrySdk)?.shutdown()?.join(10, TimeUnit.SECONDS)
    }
}

private val SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id")

internal fun buildOpenTelemetry(config: OpenTeleConfig): OpenTelemetrySdk {
    val resource = Resource.builder()
        .put(ServiceAttributes.SERVICE_NAME, "flickQ-server")
        // Several Fly machines export the same series; this keeps them apart.
        .put(SERVICE_INSTANCE_ID, InstanceId.value)
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

    val metricExporter = OtlpHttpMetricExporter.builder()
        .setEndpoint("$endpoint/v1/metrics")
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

    val meterProvider = SdkMeterProvider.builder()
        .setResource(resource)
        .registerMetricReader(
            PeriodicMetricReader.builder(metricExporter).setInterval(Duration.ofSeconds(30)).build()
        )
        .build()

    val openTele = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setLoggerProvider(loggerProvider)
        .setMeterProvider(meterProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal()

    OpenTelemetryAppender.install(openTele)

    return openTele
}
