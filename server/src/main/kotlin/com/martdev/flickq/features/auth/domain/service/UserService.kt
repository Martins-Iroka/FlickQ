package com.martdev.flickq.features.auth.domain.service

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RefreshResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput

interface UserService {
    suspend fun registerUser(credentials: Credentials): RegistrationResult
    suspend fun verifyUser(input: VerificationInput)
    suspend fun loginUser(credentials: Credentials): LoginResult
    suspend fun refreshToken(refreshToken: String): RefreshResult
    suspend fun deleteExpiredRefreshToken()
    suspend fun resendOTP(email: String): OtpResendResult
}
