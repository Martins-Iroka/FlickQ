package com.martdev.flickq.features.notification.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.notification.domain.service.DeviceService
import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.notification.model.RegisterDeviceRequest
import com.martdev.flickq.notification.request.RegisterDeviceRequestDTO
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import kotlin.time.Clock

@ExtendWith(MockKExtension::class)
class DeviceRouteTest {

    @MockK
    private lateinit var deviceService: DeviceService

    private val jwtConfig = JWTConfig("test", 15, "iss", "aud")
    private val jwt = JWTAuthImpl(jwtConfig)
    private val userToken = jwt.generateAccessToken("2", "USER")
    private val otherUserToken = jwt.generateAccessToken("3", "USER")
    private val adminToken = jwt.generateAccessToken("1", "ADMIN")
    private val fcmToken = "cXk1dEv:APA91b" + "x".repeat(140)
    private val validBody = RegisterDeviceRequestDTO(fcmToken, "ANDROID", "1.4.0", "en-NG")
    private val saved = DeviceToken(
        10, 2, fcmToken, DevicePlatform.ANDROID, "1.4.0", "en-NG",
        Clock.System.now(), Clock.System.now(), null
    )

    private val testModule = module {
        single { deviceService }
        single { jwtConfig }
    }

    @Test
    fun `PUT devices registers the token for the caller and returns 204`() = testApplication {
        val request = slot<RegisterDeviceRequest>()
        coEvery {
            deviceService.register(2, capture(request))
        } returns saved

        application {
            configure()
        }

        val response = clientConfiguration(userToken).put(devicePath) { setBody(validBody) }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(RegisterDeviceRequest(fcmToken, DevicePlatform.ANDROID, "1.4.0", "en-NG"))
            .isEqualTo(request.captured)
    }

    @Test
    fun `PUT devices accepts a lowercase platform and a POSIX-style locale`() = testApplication {
        val request = slot<RegisterDeviceRequest>()
        coEvery { deviceService.register(2, capture(request)) } returns saved
        application { configure() }

        val response = clientConfiguration(userToken).put(devicePath) {
            setBody(validBody.copy(platform = "ios", locale = "en_NG"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        assertThat(request.captured.platform).isEqualTo(DevicePlatform.IOS)
        assertThat(request.captured.locale).isEqualTo("en_NG")
    }

    @Test
    fun `PUT devices rejects invalid bodies with 400 and never reaches the service`() = testApplication {
        application { configure() }
        val client = clientConfiguration(userToken)
        val invalid = mapOf(
            "token too short" to validBody.copy(token = "x".repeat(19)),
            "token too long" to validBody.copy(token = "x".repeat(4097)),
            "blank token" to validBody.copy(token = " ".repeat(30)),
            "unknown platform" to validBody.copy(platform = "BLACKBERRY"),
            "blank app version" to validBody.copy(appVersion = ""),
            "app version too long" to validBody.copy(appVersion = "1".repeat(33)),
            "blank locale" to validBody.copy(locale = ""),
            "malformed locale" to validBody.copy(locale = "english please"),
            "locale too long" to validBody.copy(locale = "en" + "-abcdefgh".repeat(4))
        )

        invalid.forEach { (case, body) ->
            val response = client.put(devicePath) {
                setBody(body)
            }
            assertThat(response.status, case).isEqualTo(HttpStatusCode.BadRequest)
        }
        coVerify(exactly = 0) {
            deviceService.register(any(), any())
        }
    }

    @Test
    fun `PUT devices with a missing filed is a 400`() = testApplication {
        application { configure() }

        val response = clientConfiguration(userToken).put(devicePath) {
            setBody("""
                {"token":"$fcmToken","platform":"ANDROID"}
            """.trimIndent())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `PUT devices without a token is 401`() = testApplication {
        application { configure() }

        val response = clientConfiguration().put(devicePath) {
            setBody(validBody)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `PUT devices as an admin is 403`() = testApplication {
        application { configure() }

        val response = clientConfiguration(adminToken).put(devicePath) {
            setBody(validBody)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
        coVerify(exactly = 0) {
            deviceService.register(any(), any())
        }
    }

    @Test
    fun `PUT devices maps a storage failure to 500`() = testApplication {
        coEvery { deviceService.register(any(), any()) } throws InternalServerException()
        application { configure() }

        val response = clientConfiguration(userToken).put(devicePath) {
            setBody(validBody)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
    }

    @Test
    fun `PUT devices is rate limited per user`() = testApplication {
        coEvery { deviceService.register(any(), any()) } returns saved
        application { configure() }
        val user = clientConfiguration(userToken)

        repeat(10) {
            assertThat(user.put(devicePath) {
                setBody(validBody)
            }.status).isEqualTo(HttpStatusCode.NoContent)
        }
        assertThat(user.put(devicePath) {
            setBody(validBody)
        }.status).isEqualTo(HttpStatusCode.TooManyRequests)

        val other = clientConfiguration(otherUserToken).put(devicePath) {
            setBody(validBody)
        }

        assertThat(other.status).isEqualTo(HttpStatusCode.NoContent)
    }

    @Test
    fun `DELETE devices unregisters the url-encoded token for the caller and returns 204`() = testApplication {
        coJustRun { deviceService.unregister(2, fcmToken) }
        application { configure() }

        val response = clientConfiguration(userToken).delete("$devicePath/${fcmToken.encodeURLPathPart()}")

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        coVerify(exactly = 1) {
            deviceService.unregister(2, fcmToken)
        }
    }

    @Test
    fun `DELETE devices maps a storage failure to 500`() = testApplication {
        coEvery { deviceService.unregister(any(), any()) } throws InternalServerException()
        application { configure() }

        val response = clientConfiguration(userToken).delete("$devicePath/$fcmToken")

        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
    }

    private fun Application.configure() = testAppConfiguration(testModule) {
        deviceRoute()
    }
}