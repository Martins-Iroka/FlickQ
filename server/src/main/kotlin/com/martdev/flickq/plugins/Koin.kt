package com.martdev.flickq.plugins

import com.martdev.flickq.config.CorsConfig
import com.martdev.flickq.config.DatabaseConfig
import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.config.StytchConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ksp.generated.com_martdev_flickq_AppModule
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    val configModule = module {
        single { JWTConfig.fromEnvironment(environment) }
        single { DatabaseConfig.fromEnvironment(environment) }
        single { StytchConfig.fromEnvironment(environment) }
        single { PaystackConfig.fromEnvironment(environment) }
        single { CorsConfig.fromEnvironment(environment) }
    }

    install(Koin) {
        slf4jLogger()
        modules(com_martdev_flickq_AppModule, configModule)
    }
}