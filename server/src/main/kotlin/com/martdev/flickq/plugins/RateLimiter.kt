package com.martdev.flickq.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.host
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimiter() {
    install(RateLimit) {
        register(name = RateLimitName("resend-otp")) {
            rateLimiter(limit = 1, refillPeriod = 60.seconds)
        }
        register(name = RateLimitName("login")) {
            rateLimiter(limit = 5, refillPeriod = 15.minutes)
            requestKey { call -> call.request.host() }
        }
    }
}
