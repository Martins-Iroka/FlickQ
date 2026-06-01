package com.martdev.flickq.config

import io.ktor.server.application.*

/**
 * Attributes for the refresh-token cookie (Decision #2: web keeps the refresh token in a
 * Secure/HttpOnly/SameSite cookie, never in JS-readable storage). [secure] defaults to true and
 * should only be disabled for local http development; [sameSite] defaults to Strict.
 */
data class CookieConfig(
    val secure: Boolean,
    val sameSite: String,
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): CookieConfig {
            val secure = environment.config.propertyOrNull("cookie.secure")
                ?.getString()
                ?.equals("false", ignoreCase = true)
                ?.not()
                ?: true
            val sameSite = environment.config.propertyOrNull("cookie.sameSite")
                ?.getString()
                ?.takeIf { it.isNotBlank() }
                ?: "Strict"
            return CookieConfig(secure = secure, sameSite = sameSite)
        }
    }
}
