package com.martdev.flickq.features.auth.api

import com.martdev.flickq.config.CookieConfig
import com.martdev.flickq.plugins.apiV1Path
import io.ktor.http.Cookie
import io.ktor.server.routing.RoutingCall

/** Name of the httpOnly refresh-token cookie the web client relies on (it can't read it from JS). */
const val REFRESH_TOKEN_COOKIE = "refresh_token"

// Scope the cookie to the auth endpoints only — it rides along on refresh-token and logout,
// but isn't broadcast to every API call. Matches the client's `BASE_URL` + `/authentication`.
private val refreshCookiePath = "$apiV1Path/authentication"

// The refresh token lives 24h server-side (see UserServiceImpl); keep the cookie in step.
private const val REFRESH_COOKIE_MAX_AGE_SECONDS = 24 * 60 * 60

/**
 * Sets the rotated refresh token as a Secure/HttpOnly/SameSite cookie. [CookieConfig.secure] is
 * true in production; the default URI encoding round-trips with [readRefreshTokenCookie].
 */
fun RoutingCall.setRefreshTokenCookie(token: String, config: CookieConfig) {
    response.cookies.append(
        Cookie(
            name = REFRESH_TOKEN_COOKIE,
            value = token,
            maxAge = REFRESH_COOKIE_MAX_AGE_SECONDS,
            path = refreshCookiePath,
            secure = config.secure,
            httpOnly = true,
            extensions = mapOf("SameSite" to config.sameSite),
        )
    )
}

/** Expires the refresh-token cookie (maxAge 0) so the browser drops it on logout. */
fun RoutingCall.clearRefreshTokenCookie(config: CookieConfig) {
    response.cookies.append(
        Cookie(
            name = REFRESH_TOKEN_COOKIE,
            value = "",
            maxAge = 0,
            path = refreshCookiePath,
            secure = config.secure,
            httpOnly = true,
            extensions = mapOf("SameSite" to config.sameSite),
        )
    )
}

/** Reads the refresh token from the cookie (web flow); null when absent or blank. */
fun RoutingCall.readRefreshTokenCookie(): String? =
    request.cookies[REFRESH_TOKEN_COOKIE]?.takeIf { it.isNotBlank() }
