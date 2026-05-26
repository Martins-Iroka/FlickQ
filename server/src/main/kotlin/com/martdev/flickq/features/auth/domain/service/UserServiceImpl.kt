package com.martdev.flickq.features.auth.domain.service

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RefreshResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.features.auth.domain.observability.AuthEvents
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.auth.domain.security.Auth
import com.martdev.flickq.features.auth.domain.security.OTPProvider
import com.martdev.flickq.features.auth.domain.security.PasswordHasher
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.exception.UnauthorizedException
import com.martdev.flickq.shared.domain.model.DataResult
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Single
class UserServiceImpl(
    private val repository: UserRepository,
    private val otpProvider: OTPProvider,
    private val auth: Auth,
    private val passwordHasher: PasswordHasher,
    private val events: AuthEvents,
) : UserService {

    private val logger = LoggerFactory.getLogger(UserServiceImpl::class.java)
    private val invalidCredentialsMessage = "Invalid email or password"

    override suspend fun registerUser(credentials: Credentials): RegistrationResult {
        val hashedPassword = passwordHasher.hashPassword(credentials.password)
        val plainToken = UUID.randomUUID().toString()
        val tokenHash = sha256Hex(plainToken)
        val pendingUser = UserData(email = credentials.email, password = hashedPassword)

        return when (val result = repository.saveUserAndVerificationToken(pendingUser, tokenHash)) {
            is DataResult.Failure.UniqueViolation -> throw BadRequestException("Duplicate email")
            is DataResult.Failure.NotFound,
            is DataResult.Failure.ForeignKeyViolation,
            is DataResult.Failure.Conflict,
            is DataResult.Failure.UnknownError -> throw InternalServerException()

            is DataResult.Success -> {
                val saved = result.value
                val (emailId, error) = otpProvider.sendVerificationCode(saved.email)
                if (error.isNotEmpty()) {
                    events.otpSendFailed()
                    throw InternalServerException("Failed to send OTP")
                }
                events.registerSucceeded(saved.id)
                RegistrationResult(emailId = emailId, registrationToken = plainToken)
            }
        }
    }

    override suspend fun verifyUser(input: VerificationInput) {
        val (isSuccess, errorMessage) = otpProvider.verifyCode(input.emailId, input.code)
        if (!isSuccess) {
            logger.warn("OTP verification failed: {}", errorMessage)
            events.verifyFailed("otp_invalid")
            throw BadRequestException("Invalid or expired OTP")
        }
        when (repository.activateUser(sha256Hex(input.registrationToken))) {
            is DataResult.Success -> events.verifySucceeded(0)
            is DataResult.Failure.NotFound -> {
                events.verifyFailed("token_not_found")
                throw NotFoundException("Invalid or expired verification token")
            }
            is DataResult.Failure.UniqueViolation,
            is DataResult.Failure.ForeignKeyViolation,
            is DataResult.Failure.Conflict,
            is DataResult.Failure.UnknownError -> {
                events.verifyFailed("internal_error")
                throw InternalServerException("An error occurred during verification")
            }
        }
    }

    override suspend fun loginUser(credentials: Credentials): LoginResult {
        return when (val result = repository.getUserByEmail(credentials.email)) {
            is DataResult.Failure.NotFound -> {
                events.loginFailed("unknown_email")
                throw BadRequestException(invalidCredentialsMessage)
            }
            is DataResult.Failure.UniqueViolation,
            is DataResult.Failure.ForeignKeyViolation,
            is DataResult.Failure.Conflict,
            is DataResult.Failure.UnknownError -> {
                events.loginFailed("internal_error")
                throw InternalServerException()
            }

            is DataResult.Success -> {
                val savedUser = result.value

                if (!savedUser.isVerified) {
                    events.loginFailed("unverified")
                    throw BadRequestException(invalidCredentialsMessage)
                }

                if (!passwordHasher.verifyPassword(credentials.password, savedUser.password)) {
                    events.loginFailed("wrong_password")
                    throw BadRequestException(invalidCredentialsMessage)
                }

                val accessToken = auth.generateAccessToken(savedUser.id.toString(), savedUser.role.name)
                val refreshToken = auth.generateRefreshToken()
                val refreshTokenHash = sha256Hex(refreshToken)
                val refreshExpiry = Clock.System.now().plus(24.hours).toLocalDateTime(TimeZone.UTC)

                when (repository.saveRefreshToken(savedUser.id, refreshTokenHash, refreshExpiry)) {
                    is DataResult.Success -> {
                        events.loginSucceeded(savedUser.id)
                        LoginResult(
                            userId = savedUser.id,
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                        )
                    }
                    is DataResult.Failure.NotFound -> {
                        events.loginFailed("user_disappeared")
                        throw NotFoundException()
                    }
                    is DataResult.Failure.UniqueViolation,
                    is DataResult.Failure.ForeignKeyViolation,
                    is DataResult.Failure.Conflict,
                    is DataResult.Failure.UnknownError -> {
                        events.loginFailed("internal_error")
                        throw InternalServerException()
                    }
                }
            }
        }
    }

    override suspend fun refreshToken(refreshToken: String): RefreshResult {
        val oldHash = sha256Hex(refreshToken)
        val newRefreshToken = auth.generateRefreshToken()
        val newHash = sha256Hex(newRefreshToken)
        val newExpiry = Clock.System.now().plus(24.hours).toLocalDateTime(TimeZone.UTC)

        return when (val result = repository.rotateRefreshToken(oldHash, newHash, newExpiry)) {
            is DataResult.Failure.NotFound -> {
                events.refreshFailed("unknown_or_revoked")
                throw UnauthorizedException()
            }
            is DataResult.Failure.UniqueViolation,
            is DataResult.Failure.ForeignKeyViolation,
            is DataResult.Failure.Conflict,
            is DataResult.Failure.UnknownError -> {
                events.refreshFailed("internal_error")
                throw InternalServerException()
            }

            is DataResult.Success -> {
                val user = result.value
                val newAccessToken = auth.generateAccessToken(user.id.toString(), user.role.name)
                events.refreshSucceeded(user.id)
                RefreshResult(accessToken = newAccessToken, refreshToken = newRefreshToken)
            }
        }
    }

    override suspend fun deleteExpiredRefreshToken() {
        repository.deleteExpiredRefreshToken()
    }

    override suspend fun resendOTP(email: String): OtpResendResult {
        return when (val result = repository.getUserByEmail(email)) {
            is DataResult.Failure.NotFound -> OtpResendResult(emailId = "", verificationToken = "")
            is DataResult.Failure.UniqueViolation,
            is DataResult.Failure.ForeignKeyViolation,
            is DataResult.Failure.Conflict,
            is DataResult.Failure.UnknownError -> OtpResendResult(emailId = "", verificationToken = "")

            is DataResult.Success -> {
                val savedUser = result.value
                if (savedUser.isVerified) throw BadRequestException("User is already verified")

                val (emailId, error) = otpProvider.sendVerificationCode(savedUser.email)
                if (error.isNotEmpty()) {
                    events.otpSendFailed()
                    throw InternalServerException("Failed to resend OTP")
                }

                val plainToken = UUID.randomUUID().toString()
                val tokenHash = sha256Hex(plainToken)
                repository.deleteAndCreateVerificationToken(tokenHash, savedUser.id)
                events.otpResendSucceeded(savedUser.id)

                OtpResendResult(emailId = emailId, verificationToken = plainToken)
            }
        }
    }

    private fun sha256Hex(token: String): String {
        val hashedToken = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return HexFormat.of().formatHex(hashedToken)
    }
}
