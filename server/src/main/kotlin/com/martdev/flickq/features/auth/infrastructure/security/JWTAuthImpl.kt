package com.martdev.flickq.features.auth.infrastructure.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.domain.security.Auth
import org.koin.core.annotation.Single
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

@Single
class JWTAuthImpl(
    private val config: JWTConfig
) : Auth {
    override fun generateAccessToken(userID: String, role: String): String {
        val exp = config.exp
        val audience = config.audience
        val issuer = config.issuer
        val secret = config.secret
        val expirationDate = Date(System.currentTimeMillis() + exp.minutes.inWholeMilliseconds)
        val currentDate = Date(System.currentTimeMillis())

        return JWT.create()
            .withClaim("userId", userID)
            .withClaim("role", role)
            .withAudience(audience)
            .withIssuer(issuer)
            .withIssuedAt(currentDate)
            .withExpiresAt(expirationDate)
            .withNotBefore(currentDate)
            .sign(Algorithm.HMAC256(secret))
    }

    override fun generateRefreshToken(): String {
        val randomToken = Random.nextBytes(32)
        return Base64.UrlSafe.encode(randomToken)
    }
}