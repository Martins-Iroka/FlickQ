package com.martdev.flickq.feature.auth.domain

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.core.common.Result

interface AuthRepository {
    suspend fun register(credentials: Credentials): Result<RegistrationResult, AuthError>

    suspend fun verifyOtp(input: VerificationInput): Result<LoginResult, AuthError>

    suspend fun login(credentials: Credentials): Result<LoginResult, AuthError>

    suspend fun resendOtp(email: String): Result<OtpResendResult, AuthError>
}
