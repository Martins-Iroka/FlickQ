package com.martdev.flickq.plugins

import com.martdev.flickq.config.SentryConfig
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.sentry.Sentry

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
    }
    log.info("Sentry enabled: environment=${sentryConfig.environment}")
}