package com.martdev.flickq.feature.auth.data

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.auth.request.CreateUserRequest
import com.martdev.flickq.auth.request.LogoutRequest
import com.martdev.flickq.auth.request.ResendOTPRequest
import com.martdev.flickq.auth.request.UserLoginRequest
import com.martdev.flickq.auth.request.UserVerificationRequest
import com.martdev.flickq.auth.response.CreateUserResponse
import com.martdev.flickq.auth.response.ResendOTPResponse
import com.martdev.flickq.auth.response.UserLoginResponse
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.JwtDecoder
import com.martdev.flickq.core.data.TokenStorage
import com.martdev.flickq.core.data.deleteForStatus
import com.martdev.flickq.core.data.postData
import com.martdev.flickq.core.data.postForStatus
import com.martdev.flickq.core.data.postForStatusNoBody
import com.martdev.flickq.core.data.push.DeviceRegistrationStore
import com.martdev.flickq.core.data.push.PushInstallationIdSource
import com.martdev.flickq.core.data.push.PushRoutes
import com.martdev.flickq.feature.auth.domain.AuthError
import com.martdev.flickq.feature.auth.domain.AuthRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Ktor-backed [AuthRepository] hitting the `/authentication` endpoints. Login persists the issued tokens
 * to [TokenStorage] and reads the user id from the access-token JWT; verification only
 * activates the account (empty 200) and issues no session. Used when
 * [com.martdev.flickq.core.data.AppConfig.USE_FAKES] is false.
 */
class RealAuthDataSource(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage,
    private val pushInstallationIdSource: PushInstallationIdSource,
    private val deviceRegistrationStore: DeviceRegistrationStore,
) : AuthRepository {

    override suspend fun register(credentials: Credentials): Result<RegistrationResult, AuthError> =
        when (val r = client.postData<CreateUserRequest, CreateUserResponse>(
            "/authentication/register",
            CreateUserRequest(email = credentials.email, password = credentials.password)
        )) {
            is Result.Success -> Result.Success(
                RegistrationResult(emailId = r.data.emailId, registrationToken = r.data.token)
            )
            is Result.Error -> Result.Error(r.error.toRegisterError(), r.message)
        }

    override suspend fun verifyOtp(input: VerificationInput): Result<Unit, AuthError> =
        when (val r = client.postForStatus(
            "/authentication/verify-user",
            UserVerificationRequest(
                code = input.code,
                emailId = input.emailId,
                token = input.registrationToken
            )
        )) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> Result.Error(r.error.toVerifyError(), r.message)
        }

    override suspend fun login(
        credentials: Credentials,
        isAdmin: Boolean
    ): Result<LoginResult, AuthError> {
        val path = if (isAdmin) "/authentication/admin/login" else "/authentication/login"
        return when (val r = client.postData<UserLoginRequest, UserLoginResponse>(
            path,
            UserLoginRequest(email = credentials.email, password = credentials.password)
        )) {
            is Result.Success -> {
                val tokens = r.data
                tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
                val userId = JwtDecoder.decode(tokens.accessToken)?.userId?.toLongOrNull() ?: 0L
                Result.Success(
                    LoginResult(
                        userId = userId,
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken
                    )
                )
            }
            is Result.Error -> Result.Error(r.error.toLoginError(), r.message)
        }
    }

    override suspend fun newLogin(
        credentials: Credentials,
        isAdmin: Boolean
    ): Result<Unit, AuthError> {
        val path = if (isAdmin) "/authentication/admin/login" else "/authentication/login"
        return when (val r = client.postData<UserLoginRequest, UserLoginResponse>(
            path,
            UserLoginRequest(email = credentials.email, password = credentials.password)
        )) {
            is Result.Success -> {
                val tokens = r.data
                tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
                val expiryDate = JwtDecoder.decode(tokens.accessToken)?.exp
                expiryDate?.let {
                    tokenStorage.saveExpiryDate(it)
                }
                Result.Success(
                    Unit
                )
            }
            is Result.Error -> Result.Error(r.error.toLoginError(), r.message)
        }
    }

    override suspend fun resendOtp(email: String): Result<OtpResendResult, AuthError> =
        when (val r = client.postData<ResendOTPRequest, ResendOTPResponse>(
            "/authentication/resend-otp",
            ResendOTPRequest(email = email)
        )) {
            is Result.Success -> Result.Success(
                OtpResendResult(emailId = r.data.emailId, verificationToken = r.data.verificationToken)
            )
            is Result.Error -> Result.Error(AuthError.UNKNOWN, r.message)
        }

    override suspend fun logout(): Result<Unit, AuthError> {
        try {
            val fid = pushInstallationIdSource.lastKnown()
            // 1. Unregister the push installation id while the JWT is still valid (best-effort).
            if (fid != null) {
                client.deleteForStatus(PushRoutes.device(fid))
            }
            // 2. Best-effort server revoke, also carrying the id as a second unregister path:
            //    native sends the stored refresh token in the body; on web it is null and the
            //    browser's httpOnly cookie carries it.
            val refresh = tokenStorage.getRefreshToken()
            if (refresh != null || fid != null) {
                client.postForStatus("/authentication/logout", LogoutRequest(refresh, fid))
            } else {
                client.postForStatusNoBody("/authentication/logout")
            }
        } finally {
            // 3. Clear local state regardless — even if both calls fail or the caller is
            //    cancelled — so logout always succeeds locally. The id itself is kept: the next
            //    login's PUT reassigns it.
            withContext(NonCancellable) {
                tokenStorage.clear()
                deviceRegistrationStore.clear()
            }
        }
        return Result.Success(Unit)
    }
}

// Server returns 400 for a duplicate email on register.
private fun DataError.Network.toRegisterError(): AuthError = when (this) {
    DataError.Network.BAD_REQUEST, DataError.Network.CONFLICT -> AuthError.EMAIL_ALREADY_REGISTERED
    DataError.Network.NO_INTERNET -> AuthError.NO_INTERNET
    DataError.Network.REQUEST_TIMEOUT -> AuthError.TIMEOUT
    else -> AuthError.UNKNOWN
}

// 400 = invalid/expired OTP, 404 = invalid/expired verification token.
private fun DataError.Network.toVerifyError(): AuthError = when (this) {
    DataError.Network.BAD_REQUEST, DataError.Network.NOT_FOUND -> AuthError.INVALID_OTP
    DataError.Network.NO_INTERNET -> AuthError.NO_INTERNET
    DataError.Network.REQUEST_TIMEOUT -> AuthError.TIMEOUT
    else -> AuthError.UNKNOWN
}

// 400/401/404 all mean the credentials didn't authenticate.
private fun DataError.Network.toLoginError(): AuthError = when (this) {
    DataError.Network.BAD_REQUEST,
    DataError.Network.UNAUTHORIZED,
    DataError.Network.NOT_FOUND -> AuthError.INVALID_CREDENTIALS
    DataError.Network.NO_INTERNET -> AuthError.NO_INTERNET
    DataError.Network.FORBIDDEN -> AuthError.EMAIL_NOT_VERIFIED
    DataError.Network.REQUEST_TIMEOUT -> AuthError.TIMEOUT
    else -> AuthError.UNKNOWN
}
