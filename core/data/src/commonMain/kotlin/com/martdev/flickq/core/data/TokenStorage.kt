package com.martdev.flickq.core.data

/**
 * Persists auth tokens. [InMemoryTokenStorage] is used while the app runs on fakes;
 * swap for a multiplatform-settings-backed implementation when wiring the real backend.
 */
interface TokenStorage {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun saveTokens(accessToken: String, refreshToken: String)
    suspend fun clear()
    suspend fun isLoggedIn(): Boolean = getAccessToken() != null
}

class InMemoryTokenStorage : TokenStorage {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    override suspend fun getAccessToken(): String? = accessToken

    override suspend fun getRefreshToken(): String? = refreshToken

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override suspend fun clear() {
        accessToken = null
        refreshToken = null
    }
}
