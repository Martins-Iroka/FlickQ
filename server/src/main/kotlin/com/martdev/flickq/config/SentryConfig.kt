package com.martdev.flickq.config

import io.ktor.server.application.ApplicationEnvironment

data class SentryConfig(
    val dsn: String?,
    val environment: String,
    val tracesSampleRate: Double
) {
    val isEnabled: Boolean get() = !dsn.isNullOrBlank()

    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): SentryConfig {
           return SentryConfig(
               dsn = environment.getEnvValue("sentry.dsn").ifEmpty { null },
               environment = environment.getEnvValue("sentry.environment"),
               tracesSampleRate = environment.getEnvValue("sentry.trace_rate").toDouble()
           )
        }
    }
}
