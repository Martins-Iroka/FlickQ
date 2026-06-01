package com.martdev.flickq.core.data

/**
 * Web token storage (Decision #2): the access token is held in memory only and the refresh
 * token is NEVER stored client-side — it lives exclusively in a Secure/HttpOnly/SameSite
 * cookie the server sets, so XSS cannot exfiltrate it. A page refresh therefore drops the
 * in-memory access token; the next call refreshes transparently using the cookie.
 */
class WebTokenStorage : TokenStorage {
    private var accessToken: String? = null

    override suspend fun getAccessToken(): String? = accessToken

    override suspend fun getRefreshToken(): String? = null

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        // Intentionally ignore refreshToken — the cookie owns it.
        this.accessToken = accessToken
    }

    override suspend fun clear() {
        accessToken = null
    }
}
