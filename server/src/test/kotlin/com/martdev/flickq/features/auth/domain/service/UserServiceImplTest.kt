package com.martdev.flickq.features.auth.domain.service

import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.Role
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class UserServiceImplTest {

    @MockK
    private lateinit var repository: UserRepository

    @MockK
    private lateinit var otpProvider: OTPProvider

    @MockK
    private lateinit var auth: Auth

    @MockK
    private lateinit var passwordHasher: PasswordHasher

    @MockK(relaxed = true)
    private lateinit var events: AuthEvents

    private lateinit var service: UserService

    @BeforeEach
    fun setup() {
        service = UserServiceImpl(repository, otpProvider, auth, passwordHasher, events)
    }

    private val credentials = Credentials(email = "test@email.com", password = "Password123!")
    private val savedUser = UserData(
        id = 1,
        email = "test@email.com",
        password = "hashed_password",
        role = Role.USER,
        isVerified = true,
    )
    private val hashedPassword = "hashed_password"

    @Test
    fun `registerUser hashes password, hashes registration token, sends OTP and returns plain token`() = runTest {
        val savedUserSlot = slot<UserData>()
        val storedTokenSlot = slot<String>()

        every { passwordHasher.hashPassword(credentials.password) } returns hashedPassword
        coEvery {
            repository.saveUserAndVerificationToken(capture(savedUserSlot), capture(storedTokenSlot))
        } returns DataResult.Success(savedUser)
        coEvery { otpProvider.sendVerificationCode(savedUser.email) } returns Pair("emailId-from-stytch", "")

        val result = service.registerUser(credentials)

        assertEquals(credentials.email, savedUserSlot.captured.email)
        assertEquals(hashedPassword, savedUserSlot.captured.password)
        assertEquals(64, storedTokenSlot.captured.length)
        assertTrue(storedTokenSlot.captured.matches(Regex("^[0-9a-f]+$")))
        assertNotEquals(result.registrationToken, storedTokenSlot.captured)
        assertEquals("emailId-from-stytch", result.emailId)
        coVerify { events.registerSucceeded(savedUser.id) }
    }

    @Test
    fun `registerUser throws BadRequestException on duplicate email`() = runTest {
        every { passwordHasher.hashPassword(any()) } returns hashedPassword
        coEvery {
            repository.saveUserAndVerificationToken(any(), any())
        } returns DataResult.Failure.UniqueViolation

        val exception = assertFailsWith<BadRequestException> { service.registerUser(credentials) }
        assertEquals("Duplicate email", exception.error)
    }

    @Test
    fun `registerUser throws InternalServerException on unknown db error`() = runTest {
        every { passwordHasher.hashPassword(any()) } returns hashedPassword
        coEvery {
            repository.saveUserAndVerificationToken(any(), any())
        } returns DataResult.Failure.UnknownError("error")

        assertFailsWith<InternalServerException> { service.registerUser(credentials) }
    }

    @Test
    fun `registerUser throws InternalServerException and reports otp send failure when Stytch fails`() = runTest {
        every { passwordHasher.hashPassword(any()) } returns hashedPassword
        coEvery {
            repository.saveUserAndVerificationToken(any(), any())
        } returns DataResult.Success(savedUser)
        coEvery { otpProvider.sendVerificationCode(any()) } returns Pair("", "error")

        val exception = assertFailsWith<InternalServerException> { service.registerUser(credentials) }
        assertEquals("Failed to send OTP", exception.error)
        coVerify { events.otpSendFailed() }
    }

    @Test
    fun `verifyUser hashes registration token before activation`() = runTest {
        val activationTokenSlot = slot<String>()
        val plainToken = "registration-token-uuid"
        val input =
            VerificationInput(code = "123456", emailId = "emailId", registrationToken = plainToken)

        coEvery { otpProvider.verifyCode(input.emailId, input.code) } returns Pair(true, "")
        coEvery { repository.activateUser(capture(activationTokenSlot)) } returns DataResult.Success(Unit)

        service.verifyUser(input)

        assertEquals(64, activationTokenSlot.captured.length)
        assertTrue(activationTokenSlot.captured.matches(Regex("^[0-9a-f]+$")))
        assertNotEquals(plainToken, activationTokenSlot.captured)
    }

    @Test
    fun `verifyUser throws BadRequestException on OTP rejection`() = runTest {
        val input =
            VerificationInput(code = "000000", emailId = "emailId", registrationToken = "any")
        coEvery { otpProvider.verifyCode(any(), any()) } returns Pair(false, "stytch_invalid_code")

        val exception = assertFailsWith<BadRequestException> { service.verifyUser(input) }
        assertEquals("Invalid or expired OTP", exception.error)
        coVerify { events.verifyFailed("otp_invalid") }
    }

    @Test
    fun `verifyUser throws NotFoundException when token not found`() = runTest {
        val input = VerificationInput("123456", "emailId", "any")
        coEvery { otpProvider.verifyCode(any(), any()) } returns Pair(true, "")
        coEvery { repository.activateUser(any()) } returns DataResult.Failure.NotFound()

        val exception = assertFailsWith<NotFoundException> { service.verifyUser(input) }
        assertEquals("Invalid or expired verification token", exception.error)
    }

    @Test
    fun `verifyUser throws InternalServerException on other repo failures`() = runTest {
        val input = VerificationInput("123456", "emailId", "any")
        coEvery { otpProvider.verifyCode(any(), any()) } returns Pair(true, "")
        coEvery { repository.activateUser(any()) } returns DataResult.Failure.UnknownError("error")

        assertFailsWith<InternalServerException> { service.verifyUser(input) }
    }

    @Test
    fun `loginUser issues tokens, persists hashed refresh token, returns plain refresh token`() = runTest {
        val storedHashSlot = slot<String>()
        val expirySlot = slot<LocalDateTime>()
        val accessTokenValue = "access-token-value"
        val refreshTokenValue = "refresh-token-value"

        coEvery { repository.getUserByEmail(credentials.email) } returns DataResult.Success(savedUser)
        every { passwordHasher.verifyPassword(credentials.password, savedUser.password) } returns true
        every { auth.generateAccessToken(savedUser.id.toString(), savedUser.role.name) } returns accessTokenValue
        every { auth.generateRefreshToken() } returns refreshTokenValue
        coEvery {
            repository.saveRefreshToken(eq(savedUser.id), capture(storedHashSlot), capture(expirySlot))
        } returns DataResult.Success(Unit)

        val result = service.loginUser(credentials)

        assertEquals(savedUser.id, result.userId)
        assertEquals(accessTokenValue, result.accessToken)
        assertEquals(refreshTokenValue, result.refreshToken)
        assertEquals(64, storedHashSlot.captured.length)
        assertNotEquals(refreshTokenValue, storedHashSlot.captured)
        coVerify { events.loginSucceeded(savedUser.id) }
    }

    @Test
    fun `loginUser returns generic Invalid email or password for unknown email`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Failure.NotFound()

        val exception = assertFailsWith<BadRequestException> { service.loginUser(credentials) }

        assertEquals("Invalid email or password", exception.error)
        coVerify { events.loginFailed("unknown_email") }
    }

    @Test
    fun `loginUser returns generic Invalid email or password for unverified account (no enumeration leak)`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Success(savedUser.copy(isVerified = false))

        val exception = assertFailsWith<BadRequestException> { service.loginUser(credentials) }

        assertEquals("Invalid email or password", exception.error)
        coVerify { events.loginFailed("unverified") }
    }

    @Test
    fun `loginUser returns generic Invalid email or password for wrong password`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Success(savedUser)
        every { passwordHasher.verifyPassword(any(), any()) } returns false

        val exception = assertFailsWith<BadRequestException> { service.loginUser(credentials) }

        assertEquals("Invalid email or password", exception.error)
        coVerify { events.loginFailed("wrong_password") }
    }

    @Test
    fun `loginUser does not check password for unverified user`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Success(savedUser.copy(isVerified = false))

        assertFailsWith<BadRequestException> { service.loginUser(credentials) }

        coVerify(exactly = 0) { passwordHasher.verifyPassword(any(), any()) }
    }

    @Test
    fun `loginUser throws InternalServerException on db lookup failure`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Failure.UnknownError("error")

        assertFailsWith<InternalServerException> { service.loginUser(credentials) }
    }

    @Test
    fun `loginUser throws NotFoundException when saving refresh token reports user not found`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Success(savedUser)
        every { passwordHasher.verifyPassword(any(), any()) } returns true
        every { auth.generateAccessToken(any(), any()) } returns "access"
        every { auth.generateRefreshToken() } returns "refresh"
        coEvery { repository.saveRefreshToken(any(), any(), any()) } returns DataResult.Failure.NotFound()

        assertFailsWith<NotFoundException> { service.loginUser(credentials) }
    }

    @Test
    fun `refreshToken rotates the refresh token atomically via repository`() = runTest {
        val oldRefreshHashSlot = slot<String>()
        val newRefreshHashSlot = slot<String>()
        val newAccessToken = "new-access"
        val newRefreshToken = "new-refresh"

        every { auth.generateRefreshToken() } returns newRefreshToken
        every { auth.generateAccessToken(savedUser.id.toString(), savedUser.role.name) } returns newAccessToken
        coEvery {
            repository.rotateRefreshToken(
                oldTokenHash = capture(oldRefreshHashSlot),
                newTokenHash = capture(newRefreshHashSlot),
                newExpiry = any(),
            )
        } returns DataResult.Success(savedUser)

        val result = service.refreshToken("original-refresh-token")

        assertEquals(newAccessToken, result.accessToken)
        assertEquals(newRefreshToken, result.refreshToken)
        assertEquals(64, oldRefreshHashSlot.captured.length)
        assertEquals(64, newRefreshHashSlot.captured.length)
        assertNotEquals(oldRefreshHashSlot.captured, newRefreshHashSlot.captured)
        coVerify { events.refreshSucceeded(savedUser.id) }
    }

    @Test
    fun `refreshToken throws UnauthorizedException for revoked or unknown token`() = runTest {
        every { auth.generateRefreshToken() } returns "new"
        coEvery {
            repository.rotateRefreshToken(any(), any(), any())
        } returns DataResult.Failure.NotFound()

        assertFailsWith<UnauthorizedException> { service.refreshToken("anything") }
        coVerify { events.refreshFailed("unknown_or_revoked") }
    }

    @Test
    fun `refreshToken throws InternalServerException on other repo failures`() = runTest {
        every { auth.generateRefreshToken() } returns "new"
        coEvery {
            repository.rotateRefreshToken(any(), any(), any())
        } returns DataResult.Failure.UnknownError("error")

        assertFailsWith<InternalServerException> { service.refreshToken("anything") }
    }

    @Test
    fun `deleteExpiredRefreshToken delegates to repository`() = runTest {
        coEvery { repository.deleteExpiredRefreshToken() } returns DataResult.Success(Unit)

        service.deleteExpiredRefreshToken()

        coVerify { repository.deleteExpiredRefreshToken() }
    }

    @Test
    fun `resendOTP returns new emailId and plain token, persists hashed token`() = runTest {
        val persistedHashSlot = slot<String>()
        val emailIdValue = "new-email-id"

        coEvery { repository.getUserByEmail(any()) } returns DataResult.Success(savedUser.copy(isVerified = false))
        coEvery { otpProvider.sendVerificationCode(savedUser.email) } returns Pair(emailIdValue, "")
        coEvery {
            repository.deleteAndCreateVerificationToken(capture(persistedHashSlot), savedUser.id)
        } returns DataResult.Success(Unit)

        val result = service.resendOTP(savedUser.email)

        assertEquals(emailIdValue, result.emailId)
        assertEquals(64, persistedHashSlot.captured.length)
        assertNotEquals(result.verificationToken, persistedHashSlot.captured)
        coVerify { events.otpResendSucceeded(savedUser.id) }
    }

    @Test
    fun `resendOTP returns empty body for unknown email`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Failure.NotFound()

        val result = service.resendOTP("ghost@example.com")

        assertEquals("", result.emailId)
        assertEquals("", result.verificationToken)
        coVerify(exactly = 0) { otpProvider.sendVerificationCode(any()) }
    }

    @Test
    fun `resendOTP returns empty body on db lookup failure`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Failure.UnknownError("error")

        val result = service.resendOTP("any")

        assertEquals("", result.emailId)
        assertEquals("", result.verificationToken)
    }

    @Test
    fun `resendOTP throws BadRequestException for already-verified user`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Success(savedUser)

        val exception = assertFailsWith<BadRequestException> { service.resendOTP(savedUser.email) }
        assertEquals("User is already verified", exception.error)
    }

    @Test
    fun `resendOTP throws InternalServerException when Stytch send fails`() = runTest {
        coEvery { repository.getUserByEmail(any()) } returns DataResult.Success(savedUser.copy(isVerified = false))
        coEvery { otpProvider.sendVerificationCode(any()) } returns Pair("", "error")

        val exception = assertFailsWith<InternalServerException> { service.resendOTP(savedUser.email) }
        assertEquals("Failed to resend OTP", exception.error)
        coVerify { events.otpSendFailed() }
    }
}