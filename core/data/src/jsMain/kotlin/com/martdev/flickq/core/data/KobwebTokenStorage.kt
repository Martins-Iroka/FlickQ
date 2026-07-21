package com.martdev.flickq.core.data

import kotlinx.browser.sessionStorage

class KobwebTokenStorage:  TokenStorage {
    private var accessToken: String? = null

    override suspend fun getAccessToken(): String? = sessionStorage.getItem("access_token")

    override suspend fun getRefreshToken(): String? = null

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        // Intentionally ignore refreshToken — the cookie owns it.
        this.accessToken = accessToken
        sessionStorage.setItem("access_token", accessToken)
    }

    override suspend fun clear() {
        accessToken = null
        sessionStorage.clear()
    }

    override suspend fun saveExpiryDate(exp: String) {
        sessionStorage.setItem("exp", exp)
    }

    override fun getExpiryDate(): String {
        return sessionStorage.getItem("exp") ?: ""
    }
}