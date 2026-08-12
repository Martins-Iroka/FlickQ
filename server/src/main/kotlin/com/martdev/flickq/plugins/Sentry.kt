package com.martdev.flickq.plugins

import com.martdev.flickq.config.SentryConfig
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.sentry.Sentry

@Suppress("UnstableApiUsage")
fun Application.configureSentry() {
    val sentryConfig = SentryConfig.fromEnvironment(environment)
    if (!sentryConfig.isEnabled) {
        log.info("Sentry disabled (SENTRY_DSN not set)")
        return
    }
    Sentry.init { options ->
        options.dsn = sentryConfig.dsn
        options.environment = sentryConfig.environment
        options.tracesSampleRate = sentryConfig.tracesSampleRate
        options.logs.isEnabled = true
    }
    log.info("Sentry enabled: environment=${sentryConfig.environment}")
}