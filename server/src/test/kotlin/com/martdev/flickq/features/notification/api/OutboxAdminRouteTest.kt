package com.martdev.flickq.features.notification.api

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.notification.domain.model.OutboxRecord
import com.martdev.flickq.features.notification.domain.service.OutboxAdminService
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.notification.response.OutboxEntryDTO
import com.martdev.flickq.shared.DataResponse
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
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
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class OutboxAdminRouteTest {

    @MockK
    private lateinit var service: OutboxAdminService

    private val jwtConfig = JWTConfig("test", 15, "iss", "aud")
    private val jwt = JWTAuthImpl(jwtConfig)
    private val adminToken = jwt.generateAccessToken("1", "ADMIN")
    private val userToken = jwt.generateAccessToken("2", "USER")
    private val testModule = module {
        single { service }
        single { jwtConfig }
    }
    private val t0 = Instant.parse("2026-10-01T12:00:00Z")
    private val record = OutboxRecord(
        id = 9,
        type = NotificationType.BOOKING_CONFIRMED,
        targetKind = NotificationTargetKind.USER,
        status = NotificationStatus.DEAD,
        attempts = 8,
        topic = null,
        userId = 5,
        title = "Booking confirmed",
        nextAttemptAt = t0,
        expiresAt = null,
        lastError = "gave up after 8 attempts: UNAVAILABLE (503): down",
        createdAt = t0,
        sentAt = null,
    )

    private fun Application.configure() = testAppConfiguration(testModule) { outboxAdminRoute() }

    @Test
    fun `GET lists rows for an admin, status is case-insensitive`() = testApplication {
        coEvery { service.list(NotificationStatus.DEAD, 20, 0) } returns listOf(record)
        application { configure() }

        val response =
            clientConfiguration(adminToken).get("$adminOutboxPath?status=dead&limit=20&offset=0")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val dto = response.body<DataResponse<List<OutboxEntryDTO>>>().data.single()
        assertThat(dto.id).isEqualTo(9L)
        assertThat(dto.status).isEqualTo("DEAD")
        assertThat(response.bodyAsText()).doesNotContain("pending_tokens")
    }

    @Test
    fun `GET without status lists everything`() = testApplication {
        coEvery { service.list(null, 10, 0) } returns emptyList()
        application { configure() }

        val response = clientConfiguration(adminToken).get("$adminOutboxPath?limit=10&offset=0")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `GET with an unknown status is 400 and never reaches the service`() = testApplication {
        application { configure() }

        val response =
            clientConfiguration(adminToken).get("$adminOutboxPath?status=LOST&limit=10&offset=0")

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        coVerify(exactly = 0) { service.list(any(), any(), any()) }
    }

    @Test
    fun `a USER gets 403 and an anonymous caller 401`() = testApplication {
        application { configure() }

        assertThat(clientConfiguration(userToken).get("$adminOutboxPath?limit=10&offset=0").status)
            .isEqualTo(HttpStatusCode.Forbidden)
        assertThat(clientConfiguration().post("$adminOutboxPath/9/retry").status)
            .isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `POST retry requeues as the calling admin and returns 204`() = testApplication {
        coJustRun { service.retry(9, 1) }
        application { configure() }

        val response = clientConfiguration(adminToken).post("$adminOutboxPath/9/retry")

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
        coVerify { service.retry(9, 1) }
    }

    @Test
    fun `POST retry maps not found to 404, not DEAD to 409 and a bad id to 400`() =
        testApplication {
            coEvery {
                service.retry(
                    404,
                    any()
                )
            } throws NotFoundException("outbox row 404 not found")
            coEvery { service.retry(409, any()) } throws ConflictException("outbox row 409 is SENT")
            application { configure() }
            val client = clientConfiguration(adminToken)

            assertThat(client.post("$adminOutboxPath/404/retry").status).isEqualTo(HttpStatusCode.NotFound)
            assertThat(client.post("$adminOutboxPath/409/retry").status).isEqualTo(HttpStatusCode.Conflict)
            assertThat(client.post("$adminOutboxPath/abc/retry").status).isEqualTo(HttpStatusCode.BadRequest)
        }
}