package com.martdev.flickq.utils

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
    /*configureSerialization()
    configureStatusPages()
    configureSecurity()
    configureRateLimiter()
    configureRequestValidation()*/
    routing {
        block()
    }
}