package com.martdev.flickq.features.auth.infrastructure.security

import com.martdev.flickq.config.StytchConfig
import com.martdev.flickq.features.auth.domain.security.OTPProvider
import com.stytch.java.common.StytchResult
import com.stytch.java.consumer.StytchClient
import com.stytch.java.consumer.models.otp.AuthenticateRequest
import com.stytch.java.consumer.models.otpemail.LoginOrCreateRequest
import io.ktor.http.HttpStatusCode
import org.koin.core.annotation.Single

@Single
class StytchOTPProvider(
    stytchConfig: StytchConfig
) : OTPProvider {

    private val client = StytchClient(
        stytchConfig.projectId,
        stytchConfig.secret
    )
    override suspend fun sendVerificationCode(email: String): Pair<String, String> {
        val result = client.otps.email.loginOrCreate(
            LoginOrCreateRequest(email)
        )
        return when(result) {
            is StytchResult.Error -> Pair("", result.exception.message?: "Error")
            is StytchResult.Success -> Pair(result.value.emailId, "")
        }
    }

    override suspend fun verifyCode(
        emailID: String,
        code: String
    ): Pair<Boolean, String> {
        val result = client.otps.authenticate(
            AuthenticateRequest(emailID, code)
        )
        return when(result) {
            is StytchResult.Error -> Pair(false, result.exception.message ?: "Error")
            is StytchResult.Success -> verifyCode(result.value.statusCode)
        }
    }

    private fun verifyCode(code: Int) = when(code) {
        in 400..507 -> Pair(false, HttpStatusCode.fromValue(code).description)
        else -> Pair(true, "")
    }
}