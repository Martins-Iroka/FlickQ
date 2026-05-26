package com.martdev.flickq.features.auth.domain.security

interface OTPProvider {
    suspend fun sendVerificationCode(email: String): Pair<String, String>
    suspend fun verifyCode(emailID: String, code: String): Pair<Boolean, String>
}