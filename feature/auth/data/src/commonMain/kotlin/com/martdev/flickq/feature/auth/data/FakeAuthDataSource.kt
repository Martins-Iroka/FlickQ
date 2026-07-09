package com.martdev.flickq.feature.auth.data

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.TokenStorage
import com.martdev.flickq.feature.auth.data.FakeAuthDataSource.Companion.VALID_OTP
import com.martdev.flickq.feature.auth.domain.AuthError
import com.martdev.flickq.feature.auth.domain.AuthRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory stand-in for the real auth backend. Accepts any registration, treats
 * [VALID_OTP] as the correct verification code, and persists issued tokens to
 * [TokenStorage]. Swapped for a Ktor-backed implementation when wiring the real API.
 */
class FakeAuthDataSource(
    private val tokenStorage: TokenStorage
) : AuthRepository {

    private data class Account(val password: String, var verified: Boolean)

    // Seed a known admin so the admin app is demoable on fakes (no admin registration UI exists).
    private val accounts = mutableMapOf(
        SEED_ADMIN_EMAIL to Account(SEED_ADMIN_PASSWORD, verified = true),
    )
    private var userIdSeq = 1L

    override suspend fun register(credentials: Credentials): Result<RegistrationResult, AuthError> {
        if (accounts.containsKey(credentials.email)) {
            return Result.Error(AuthError.EMAIL_ALREADY_REGISTERED)
        }
        accounts[credentials.email] = Account(credentials.password, verified = false)
        return Result.Success(
            RegistrationResult(
                emailId = credentials.email,
                registrationToken = "reg-token-${credentials.email}"
            )
        )
    }

    override suspend fun verifyOtp(input: VerificationInput): Result<Unit, AuthError> {
        val account = accounts[input.emailId] ?: return Result.Error(AuthError.UNKNOWN)
        if (input.code != VALID_OTP) {
            return Result.Error(AuthError.INVALID_OTP)
        }
        // Mirrors the real backend: verification activates the account but issues no session;
        // the user logs in afterwards.
        account.verified = true
        return Result.Success(Unit)
    }

    override suspend fun login(
        credentials: Credentials,
        isAdmin: Boolean
    ): Result<LoginResult, AuthError> {
        val account = accounts[credentials.email]
        if (account == null || account.password != credentials.password) {
            return Result.Error(AuthError.INVALID_CREDENTIALS)
        }
        return issueSession(credentials.email, isAdmin)
    }

    override suspend fun resendOtp(email: String): Result<OtpResendResult, AuthError> {
        if (!accounts.containsKey(email)) {
            return Result.Error(AuthError.UNKNOWN)
        }
        return Result.Success(
            OtpResendResult(
                emailId = email,
                verificationToken = "reg-token-$email"
            )
        )
    }

    override suspend fun logout(): Result<Unit, AuthError> {
        tokenStorage.clear()
        return Result.Success(Unit)
    }

    private suspend fun issueSession(email: String, isAdmin: Boolean = false): Result<LoginResult, AuthError> {
        val userId = userIdSeq++
        // Issue a real (signature-less) JWT so JwtDecoder can read the role — the admin app gates
        // entry on the ADMIN claim. Mirrors the shape of the backend's tokens.
        val accessToken = fakeJwt(userId, role = if (isAdmin) "ADMIN" else "USER")
        val refreshToken = "refresh-token-$email"
        tokenStorage.saveTokens(accessToken = accessToken, refreshToken = refreshToken)
        return Result.Success(
            LoginResult(
                userId = userId,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun fakeJwt(userId: Long, role: String): String {
        val exp = Clock.System.now().plus(30.minutes).toLocalDateTime(TimeZone.currentSystemDefault()).toString().plus("Z")

        val payload = """{"userId":"$userId","role":"$role","exp":"$exp"}"""
        val body = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray())
        return "header.$body.signature"
    }

    companion object {
        const val VALID_OTP = "123456"
        const val SEED_ADMIN_EMAIL = "admin@flickq.com"
        const val SEED_ADMIN_PASSWORD = "Admin123"
    }
}
