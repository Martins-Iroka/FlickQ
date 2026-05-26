package com.martdev.flickq

import com.martdev.flickq.plugins.configureBackgroundJobs
import com.martdev.flickq.plugins.configureCallLogging
import com.martdev.flickq.plugins.configureDatabase
import com.martdev.flickq.plugins.configureHttp
import com.martdev.flickq.plugins.configureKoin
import com.martdev.flickq.plugins.configureMonitoring
import com.martdev.flickq.plugins.configureRateLimiter
import com.martdev.flickq.plugins.configureRequestValidation
import com.martdev.flickq.plugins.configureRouting
import com.martdev.flickq.plugins.configureSecurity
import com.martdev.flickq.plugins.configureSerialization
import com.martdev.flickq.plugins.configureStatusPages
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.Application

fun main(args: Array<String>) {
    dotenv {
        systemProperties = true
    }
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    configureCallLogging()
    configureHttp()
    configureDatabase()
    configureSecurity()
    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureRateLimiter()
    configureRouting()
    configureRequestValidation()
    configureBackgroundJobs()
}