package com.martdev.flickq.config

import io.ktor.server.application.ApplicationEnvironment

data class OpenTeleConfig(
    val endpoint: String,
    val apiKey: String,
    val authKey: String
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): OpenTeleConfig {
            return OpenTeleConfig(
                environment.getEnvValue("openTele.endpoint"),
                environment.getEnvValue("openTele.api"),
                environment.getEnvValue("openTele.auth")
            )
        }
    }
}
