package com.martdev.flickq.feature.auth.domain

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result

interface AuthRepository {
    suspend fun register(credentials: Credentials): Result<RegistrationResult, AuthError>

    /**
     * Activates the account for the given code. The backend's `verify-user` returns an empty
     * 200 and issues **no** session — verification and login are separate steps — so the user
     * is sent to log in afterward. Hence [EmptyResult] rather than a [LoginResult].
     */
    suspend fun verifyOtp(input: VerificationInput): EmptyResult<AuthError>

    suspend fun login(credentials: Credentials): Result<LoginResult, AuthError>

    suspend fun resendOtp(email: String): Result<OtpResendResult, AuthError>

    /**
     * Ends the session: best-effort revokes the refresh token server-side and always clears the
     * local tokens. Returns [EmptyResult] success even if the network call fails — a user must be
     * able to log out regardless of connectivity.
     */
    suspend fun logout(): EmptyResult<AuthError>
}
