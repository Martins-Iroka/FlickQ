package com.martdev.flickq.config

import io.ktor.server.application.*

data class CorsConfig(
    val allowedHosts: List<String>,
    val allowAnyHost: Boolean,
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): CorsConfig {
            val rawHosts = environment.config.propertyOrNull("cors.allowedHosts")?.getString().orEmpty()
            val allowAnyHost = environment.config.propertyOrNull("cors.allowAnyHost")
                ?.getString()
                ?.equals("true", ignoreCase = true) == true
            val hosts = rawHosts
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            return CorsConfig(allowedHosts = hosts, allowAnyHost = allowAnyHost)
        }
    }
}
