package com.martdev.flickq.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

fun Application.configureRateLimiter() {
    install(RateLimit) {
        register(name = RateLimitName("resend-otp")) {
            rateLimiter(limit = 1, refillPeriod = 1.minutes)
        }
        register(name = RateLimitName("login")) {
            rateLimiter(limit = 5, refillPeriod = 15.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(RateLimitName("devices")) {
            rateLimiter(10, 1.minutes)
            requestKey { call ->
                call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: call.request.origin.remoteHost
            }
        }
    }
}
