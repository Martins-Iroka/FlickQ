package com.martdev.flickq.features.auth.infrastructure.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.martdev.flickq.config.JWTConfig
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JWTAuthImplTest {

    private val config = JWTConfig(
        secret = "this-is-a-test-secret-long-enough",
        exp = 15,
        issuer = "iss",
        audience = "aud",
    )
    private val auth = JWTAuthImpl(config)

    @Test
    fun `generateAccessToken sets userId, role, audience, issuer and signs with the configured secret`() {
        val token = auth.generateAccessToken("42", "ADMIN")

        val decoded = JWT.require(Algorithm.HMAC256(config.secret))
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .build()
            .verify(token)

        assertEquals("42", decoded.getClaim("userId").asString())
        assertEquals("ADMIN", decoded.getClaim("role").asString())
        assertEquals(listOf(config.audience), decoded.audience)
        assertEquals(config.issuer, decoded.issuer)
    }

    @Test
    fun `generateAccessToken expiry honours JWTConfig exp (within tolerance)`() {
        val beforeSec = System.currentTimeMillis() / 1000
        val token = auth.generateAccessToken("1", "USER")
        val afterSec = System.currentTimeMillis() / 1000 + 1

        val decoded = JWT.decode(token)
        // JWT exp is encoded as Unix seconds (NumericDate), so compare in seconds.
        val expSec = decoded.expiresAtAsInstant.epochSecond
        val minExpected = beforeSec + config.exp * 60
        val maxExpected = afterSec + config.exp * 60

        assertTrue(
            expSec in minExpected..maxExpected,
            "exp=$expSec not in [$minExpected, $maxExpected]",
        )
    }

    @Test
    fun `generateAccessToken signature fails with a different secret`() {
        val token = auth.generateAccessToken("1", "USER")
        val verifier = JWT.require(Algorithm.HMAC256("a-different-secret-also-32-chars-long"))
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .build()

        assertFailsWith<SignatureVerificationException> { verifier.verify(token) }
    }

    @Test
    fun `generateAccessToken sets nbf and iat (not-before and issued-at) claims`() {
        val token = auth.generateAccessToken("1", "USER")
        val decoded = JWT.decode(token)

        assertTrue(decoded.notBefore != null, "nbf should be set")
        assertTrue(decoded.issuedAt != null, "iat should be set")
        assertTrue(
            decoded.notBefore.toInstant() == decoded.issuedAt.toInstant(),
            "nbf should equal iat",
        )
    }

    @Test
    fun `generateRefreshToken returns a unique URL-safe base64 token of 32 random bytes`() {
        val a = auth.generateRefreshToken()
        val b = auth.generateRefreshToken()

        assertNotEquals(a, b, "Two consecutive refresh tokens should differ")
        val decoded = Base64.getUrlDecoder().decode(a)
        assertEquals(32, decoded.size, "Refresh token should decode to 32 random bytes")
    }
}
