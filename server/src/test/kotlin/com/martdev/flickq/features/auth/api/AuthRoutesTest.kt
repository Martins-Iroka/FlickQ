package com.martdev.flickq.features.auth.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RefreshResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.request.CreateUserRequest
import com.martdev.flickq.auth.request.LogoutRequest
import com.martdev.flickq.auth.request.RefreshTokenRequest
import com.martdev.flickq.auth.request.ResendOTPRequest
import com.martdev.flickq.auth.request.UserLoginRequest
import com.martdev.flickq.auth.request.UserVerificationRequest
import com.martdev.flickq.config.CookieConfig
import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.domain.service.UserService
import com.martdev.flickq.features.notification.domain.service.DeviceService
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.exception.UnauthorizedException
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class AuthRoutesTest {

    @MockK
    private lateinit var service: UserService

    @MockK
    private lateinit var deviceService: DeviceService

    private val jwtConfig = JWTConfig("test", 15, "iss", "aud")
    private val cookieConfig = CookieConfig(secure = true, sameSite = "Strict")
    private val deviceToken = "cXk1dEv:APA91b" + "x".repeat(140)

    private val testModule by lazy {
        module {
            single<UserService> { service }
            single<DeviceService> { deviceService }
            single { jwtConfig }
            single { cookieConfig }
        }
    }

    private fun Application.configure() = testAppConfiguration(testModule) { authRoutes() }

    @Test
    fun `POST register returns 201 with emailId and token`() = testApplication {
        coEvery { service.registerUser(any()) } returns RegistrationResult(
            emailId = "stytch-email-id",
            registrationToken = "registration-token",
        )
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/register") {
            setBody(CreateUserRequest(email = "new@example.com", password = "Password123!"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        coVerify { service.registerUser(any()) }
    }

    @Test
    fun `POST register returns 400 for invalid email format`() = testApplication {
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/register") {
            setBody(CreateUserRequest(email = "not-an-email", password = "Password123!"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST register returns 400 for weak password`() = testApplication {
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/register") {
            setBody(CreateUserRequest(email = "user@example.com", password = "short"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST register propagates duplicate-email BadRequestException as 400`() = testApplication {
        coEvery { service.registerUser(any()) } throws BadRequestException("Duplicate email")
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/register") {
            setBody(CreateUserRequest(email = "dupe@example.com", password = "Password123!"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST register propagates OTP failure as 500`() = testApplication {
        coEvery { service.registerUser(any()) } throws InternalServerException("Failed to send OTP")
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/register") {
            setBody(CreateUserRequest(email = "user@example.com", password = "Password123!"))
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `POST verify-user returns 200 on successful verification`() = testApplication {
        coJustRun { service.verifyUser(any()) }
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/verify-user") {
            setBody(
                UserVerificationRequest(
                    code = "123456",
                    emailId = "stytch-email-id",
                    token = "tok"
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST verify-user returns 400 for invalid OTP`() = testApplication {
        coEvery { service.verifyUser(any()) } throws BadRequestException("Invalid or expired OTP")
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/verify-user") {
            setBody(UserVerificationRequest(code = "000000", emailId = "stytch-email-id", token = "tok"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST verify-user returns 404 for unknown registration token`() = testApplication {
        coEvery { service.verifyUser(any()) } throws NotFoundException("Invalid or expired verification token")
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/verify-user") {
            setBody(UserVerificationRequest(code = "123456", emailId = "stytch-email-id", token = "tok"))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST verify-user returns 400 for invalid code length`() = testApplication {
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/verify-user") {
            setBody(UserVerificationRequest(code = "12", emailId = "stytch-email-id", token = "tok"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST login returns 200 with tokens on success`() = testApplication {
        coEvery { service.loginUser(any()) } returns LoginResult(
            userId = 1L,
            accessToken = "access",
            refreshToken = "refresh",
        )
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/login") {
            setBody(UserLoginRequest(email = "user@example.com", password = "Password123!"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST login returns 400 for invalid credentials (generic message)`() = testApplication {
        coEvery { service.loginUser(any()) } throws BadRequestException("Invalid email or password")
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/login") {
            setBody(UserLoginRequest(email = "user@example.com", password = "Password123!"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST login rate-limit returns 429 after threshold`() = testApplication {
        coEvery { service.loginUser(any()) } throws BadRequestException("Invalid email or password")
        application { configure() }
        val client = clientConfiguration()

        // First 5 should be allowed (and return 400 due to invalid creds), 6th should be rate-limited
        repeat(5) {
            client.post("/authentication/login") {
                setBody(UserLoginRequest(email = "user@example.com", password = "Password123!"))
            }
        }
        val sixth = client.post("/authentication/login") {
            setBody(UserLoginRequest(email = "user@example.com", password = "Password123!"))
        }

        assertEquals(HttpStatusCode.TooManyRequests, sixth.status)
    }

    @Test
    fun `POST refresh-token returns 200 with new tokens`() = testApplication {
        coEvery { service.refreshToken("valid-refresh") } returns RefreshResult(
            accessToken = "new-access",
            refreshToken = "new-refresh",
        )
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/refresh-token") {
            setBody(RefreshTokenRequest(refreshToken = "valid-refresh"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST login sets a Secure HttpOnly SameSite refresh-token cookie`() = testApplication {
        coEvery { service.loginUser(any()) } returns LoginResult(
            userId = 1L,
            accessToken = "access",
            refreshToken = "refresh-cookie-value",
        )
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/login") {
            setBody(UserLoginRequest(email = "user@example.com", password = "Password123!"))
        }

        val setCookie = response.headers[HttpHeaders.SetCookie]
        assertNotNull(setCookie)
        assertTrue(setCookie.contains("refresh_token=refresh-cookie-value"))
        assertTrue(setCookie.contains("HttpOnly", ignoreCase = true))
        assertTrue(setCookie.contains("Secure", ignoreCase = true))
        assertTrue(setCookie.contains("SameSite=Strict"))
    }

    @Test
    fun `POST refresh-token reads the token from the cookie when no body is sent`() = testApplication {
        coEvery { service.refreshToken("cookie-refresh") } returns RefreshResult(
            accessToken = "new-access",
            refreshToken = "rotated-refresh",
        )
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/refresh-token") {
            header(HttpHeaders.Cookie, "refresh_token=cookie-refresh")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { service.refreshToken("cookie-refresh") }
        // The rotated token is written back to the cookie.
        assertEquals(
            response.headers[HttpHeaders.SetCookie]?.contains("refresh_token=rotated-refresh"),
            true
        )
    }

    @Test
    fun `POST refresh-token returns 401 when neither body nor cookie carries a token`() = testApplication {
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/refresh-token")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST logout revokes the cookie token and expires the cookie`() = testApplication {
        coEvery { service.logout(any()) } returns null
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/logout") {
            header(HttpHeaders.Cookie, "refresh_token=cookie-refresh")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { service.logout("cookie-refresh") }
        assertEquals(response.headers[HttpHeaders.SetCookie]?.contains("Max-Age=0"), true)
    }

    @Test
    fun `POST logout returns 200 and skips revocation when no token is present`() = testApplication {
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/logout")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 0) { service.logout(any()) }
    }

    @Test
    fun `POST refresh-token returns 401 for invalid token`() = testApplication {
        coEvery { service.refreshToken(any()) } throws UnauthorizedException()
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/refresh-token") {
            setBody(RefreshTokenRequest(refreshToken = "invalid-refresh"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST refresh-token returns 400 for empty token`() = testApplication {
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/refresh-token") {
            setBody(RefreshTokenRequest(refreshToken = ""))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST resend-otp returns 200 on success`() = testApplication {
        coEvery { service.resendOTP(any()) } returns OtpResendResult(
            emailId = "stytch-email-id",
            verificationToken = "tok",
        )
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/resend-otp") {
            setBody(ResendOTPRequest(email = "user@example.com"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST resend-otp returns 200 with empty body for unknown email (no enumeration)`() = testApplication {
        coEvery { service.resendOTP(any()) } returns OtpResendResult(emailId = "", verificationToken = "")
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/resend-otp") {
            setBody(ResendOTPRequest(email = "ghost@example.com"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST resend-otp returns 400 for already-verified user`() = testApplication {
        coEvery { service.resendOTP(any()) } throws BadRequestException("User is already verified")
        application { configure() }
        val client = clientConfiguration()

        val response = client.post("/authentication/resend-otp") {
            setBody(ResendOTPRequest(email = "user@example.com"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST resend-otp rate-limit returns 429 on second request within window`() = testApplication {
        coEvery { service.resendOTP(any()) } returns OtpResendResult(
            emailId = "stytch-email-id",
            verificationToken = "tok",
        )
        application { configure() }
        val client = clientConfiguration()

        val first = client.post("/authentication/resend-otp") {
            setBody(ResendOTPRequest(email = "user@example.com"))
        }
        val second = client.post("/authentication/resend-otp") {
            setBody(ResendOTPRequest(email = "user@example.com"))
        }

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.TooManyRequests, second.status)
    }

    @Test
    fun `POST logout with a device token unregisters it for the refresh token's owner`() = testApplication {
        coEvery { service.logout("native-refresh") } returns 2L
        coJustRun { deviceService.unregister(2, deviceToken) }
        application { configure() }

        val response = clientConfiguration().post("/authentication/logout") {
            setBody(LogoutRequest("native-refresh", deviceToken))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        coVerify(exactly = 1) {
            deviceService.unregister(2, deviceToken)
        }
    }

    @Test
    fun `POST logout on web takes the refresh token from the cookie and the device token from the body`() = testApplication {
        coEvery { service.logout("cookie-refresh") } returns 2L
        coJustRun { deviceService.unregister(2, deviceToken) }
        application { configure() }

        val response = clientConfiguration().post("/authentication/logout") {
            header(HttpHeaders.Cookie, "refresh_token=cookie-refresh")
            setBody(LogoutRequest(deviceToken = deviceToken))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        coVerify { service.logout("cookie-refresh") }
        coVerify(exactly = 1) { deviceService.unregister(2, deviceToken) }
    }

    @Test
    fun `POST logout leaves devices alone when the refresh token is unknown`() = testApplication {
        coEvery { service.logout(any()) } returns null
        application { configure() }

        val response = clientConfiguration().post("/authentication/logout") {
            setBody(LogoutRequest(refreshToken = "revoked", deviceToken))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        coVerify(exactly = 0) { deviceService.unregister(any(), any()) }
    }

    @Test
    fun `POST logout still accepts the legacy refresh-token-only body`() = testApplication {
        coEvery { service.logout("legacy") } returns 2L
        application { configure() }

        val response = clientConfiguration().post("/authentication/logout") {
            setBody(RefreshTokenRequest(refreshToken = "legacy"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        coVerify { service.logout("legacy") }
        coVerify(exactly = 0) { deviceService.unregister(any(), any()) }
    }
}
