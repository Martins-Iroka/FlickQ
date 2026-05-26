package com.martdev.flickq.config

import io.ktor.server.application.*

data class JWTConfig(
    val secret: String,
    val exp: Long,
    val issuer: String,
    val audience: String,
) {
    init {
        require(secret.isNotBlank()) { "jwt.secret is required and cannot be blank" }
        require(issuer.isNotBlank()) { "jwt.issuer is required and cannot be blank" }
        require(audience.isNotBlank()) { "jwt.audience is required and cannot be blank" }
        require(exp > 0) { "jwt.expirationMinutes must be positive" }
    }

    companion object {
        private const val MIN_SECRET_LENGTH = 32

        fun fromEnvironment(environment: ApplicationEnvironment): JWTConfig {
            val secret = environment.getEnvValue("jwt.secret")
            require(secret.length >= MIN_SECRET_LENGTH) {
                "jwt.secret must be at least $MIN_SECRET_LENGTH characters for HMAC256 strength"
            }
            return JWTConfig(
                secret = secret,
                exp = environment.getEnvValue("jwt.expirationMinutes").toLongOrNull()
                    ?: error("jwt.expirationMinutes must be a positive integer"),
                issuer = environment.getEnvValue("jwt.issuer"),
                audience = environment.getEnvValue("jwt.audience"),
            )
        }
    }
}
