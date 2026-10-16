package com.martdev.flickq.feature.notification.data

import com.martdev.flickq.core.data.JwtDecoder
import com.martdev.flickq.core.data.TokenStorage
import com.martdev.flickq.feature.notification.domain.PushSession
import com.martdev.flickq.feature.notification.domain.PushSessionProvider

/**
 * Reads the session from the stored access token. The JWT is decoded unverified, which is fine
 * here: it only decides whether to *try* registering; the server's role guard decides the rest.
 */
class TokenPushSessionProvider(
    private val tokenStorage: TokenStorage,
) : PushSessionProvider {

    override suspend fun current(): PushSession? {
        val claims = JwtDecoder.decode(tokenStorage.getAccessToken()) ?: return null
        val userId = claims.userId?.takeIf { it.isNotBlank() } ?: return null
        return PushSession(userId = userId, isCustomer = claims.role == ROLE_USER)
    }

    private companion object {
        const val ROLE_USER = "USER"
    }
}