package com.martdev.flickq.utils

import com.martdev.flickq.plugins.configureRateLimiter
import com.martdev.flickq.plugins.configureRequestValidation
import com.martdev.flickq.plugins.configureSecurity
import com.martdev.flickq.plugins.configureSerialization
import com.martdev.flickq.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import org.koin.core.module.Module
import org.koin.ktor.plugin.Koin

inline fun Application.testAppConfiguration(module: Module, crossinline block: Route.() -> Unit) {
    install(Koin) {
        modules(module)
    }
    configureSerialization()
    configureStatusPages()
    configureSecurity()
    configureRateLimiter()
    configureRequestValidation()
    routing {
        block()
    }
}