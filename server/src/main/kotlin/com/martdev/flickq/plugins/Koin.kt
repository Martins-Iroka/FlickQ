package com.martdev.flickq.plugins

import com.martdev.flickq.config.CookieConfig
import com.martdev.flickq.config.CorsConfig
import com.martdev.flickq.config.DatabaseConfig
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.config.OpenTeleConfig
import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.config.SeedConfig
import com.martdev.flickq.config.StytchConfig
import com.martdev.flickq.features.notification.infrastructure.fcm.fcmModule
import com.martdev.flickq.shared.api.httpClientModule
import com.martdev.flickq.shared.infrastruce.telemetry.telemetryModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.core.annotation.KoinApplication
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.koin.plugin.module.dsl.withConfiguration

@KoinApplication
object KoinFlickQApplication

fun Application.configureKoin() {
    val configModule = module {
        single { JWTConfig.fromEnvironment(environment) }
        single { DatabaseConfig.fromEnvironment(environment) }
        single { StytchConfig.fromEnvironment(environment) }
        single { PaystackConfig.fromEnvironment(environment) }
        single { CorsConfig.fromEnvironment(environment) }
        single { CookieConfig.fromEnvironment(environment) }
        single { OpenTeleConfig.fromEnvironment(environment) }
        single { SeedConfig.fromEnvironment() }
        single { FcmConfig.fromEnvironment(environment) }
    }

    install(Koin) {
        slf4jLogger()
        withConfiguration<KoinFlickQApplication>()
        modules(configModule, httpClientModule, fcmModule, telemetryModule)
    }
}