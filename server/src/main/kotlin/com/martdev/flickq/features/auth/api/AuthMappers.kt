package com.martdev.flickq.features.auth.api

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RefreshResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.auth.request.CreateUserRequest
import com.martdev.flickq.auth.request.UserLoginRequest
import com.martdev.flickq.auth.request.UserVerificationRequest
import com.martdev.flickq.auth.response.CreateUserResponse
import com.martdev.flickq.auth.response.RefreshTokenResponse
import com.martdev.flickq.auth.response.ResendOTPResponse
import com.martdev.flickq.auth.response.UserLoginResponse

fun CreateUserRequest.toCredentials() = Credentials(
    email = email,
    password = password
)

fun RegistrationResult.toCreateUserResponse() = CreateUserResponse(
    emailId = emailId,
    token = registrationToken
)

fun UserVerificationRequest.toVerificationInput() = VerificationInput(
    code = code,
    emailId = emailId,
    registrationToken = token
)

fun UserLoginRequest.toCredentials() = Credentials(
    email = email,
    password = password
)

fun LoginResult.toUserLoginResponse() = UserLoginResponse(
    accessToken = accessToken,
    refreshToken = refreshToken
)

fun RefreshResult.toRefreshTokenResponse() = RefreshTokenResponse(
    accessToken = accessToken,
    refreshToken = refreshToken
)

fun OtpResendResult.toResendOTPResponse() = ResendOTPResponse(
    emailId = emailId,
    verificationToken = verificationToken
)
