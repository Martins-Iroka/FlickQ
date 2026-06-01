package com.martdev.flickq.feature.auth.data

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.TokenStorage
import com.martdev.flickq.feature.auth.domain.AuthError
import com.martdev.flickq.feature.auth.domain.AuthRepository

/**
 * In-memory stand-in for the real auth backend. Accepts any registration, treats
 * [VALID_OTP] as the correct verification code, and persists issued tokens to
 * [TokenStorage]. Swapped for a Ktor-backed implementation when wiring the real API.
 */
class FakeAuthDataSource(
    private val tokenStorage: TokenStorage
) : AuthRepository {

    private data class Account(val password: String, var verified: Boolean)

    private val accounts = mutableMapOf<String, Account>()
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

    override suspend fun login(credentials: Credentials): Result<LoginResult, AuthError> {
        val account = accounts[credentials.email]
        if (account == null || account.password != credentials.password) {
            return Result.Error(AuthError.INVALID_CREDENTIALS)
        }
        return issueSession(credentials.email)
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

    private suspend fun issueSession(email: String): Result<LoginResult, AuthError> {
        val userId = userIdSeq++
        val accessToken = "access-token-$email"
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

    companion object {
        const val VALID_OTP = "123456"
    }
}
