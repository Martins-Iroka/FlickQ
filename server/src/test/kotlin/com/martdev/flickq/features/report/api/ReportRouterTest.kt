package com.martdev.flickq.features.report.api

import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.report.domain.service.ReportService
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueReport
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class ReportRouterTest {

    @MockK
    private lateinit var reportService: ReportService

    private val jwtConfig = JWTConfig("test", 15, "iss", "aud")
    private val jwt = JWTAuthImpl(jwtConfig)
    private val userToken = jwt.generateAccessToken("2", "USER")
    private val adminToken = jwt.generateAccessToken("1", "ADMIN")

    private val reportModule = module {
        single { reportService }
        single { jwtConfig }
    }

    private val from = Instant.parse("2026-05-01T00:00:00Z")
    private val to = Instant.parse("2026-05-31T00:00:00Z")

    @Test
    fun `GET revenue 401 without token`() = testApplication {
        application { appConfig() }
        val client = clientConfiguration()
        client.get("/admin/reports/revenue?from=$from&to=$to").apply {
            assertEquals(HttpStatusCode.Unauthorized, status, bodyAsText())
        }
    }

    @Test
    fun `GET revenue 403 for non-admin`() = testApplication {
        application { appConfig() }
        val client = clientConfiguration(userToken)
        client.get("/admin/reports/revenue?from=$from&to=$to").apply {
            assertEquals(HttpStatusCode.Forbidden, status, bodyAsText())
        }
    }

    @Test
    fun `GET revenue 200 for admin returns snake_case payload`() = testApplication {
        coEvery {
            reportService.getRevenueReport(from, to, ReportBucketGranularity.DAY)
        } returns RevenueReport(
            from = from, to = to,
            bucket = ReportBucketGranularity.DAY, currency = "NGN",
            buckets = emptyList(),
            totalGross = 0, totalRefunds = 0, totalNet = 0, totalTicketsSold = 0,
        )

        application { appConfig() }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reports/revenue?from=$from&to=$to").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
            val body = bodyAsText()
            assertTrue("total_gross" in body, "expected snake_case total_gross in $body")
            assertTrue("total_tickets_sold" in body)
        }
    }

    @Test
    fun `GET revenue 400 on missing from`() = testApplication {
        application { appConfig() }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reports/revenue?to=$to").apply {
            assertEquals(HttpStatusCode.BadRequest, status, bodyAsText())
        }
    }

    @Test
    fun `GET revenue 400 on malformed to`() = testApplication {
        application { appConfig() }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reports/revenue?from=$from&to=not-a-date").apply {
            assertEquals(HttpStatusCode.BadRequest, status, bodyAsText())
        }
    }

    @Test
    fun `GET revenue 400 on invalid bucket`() = testApplication {
        application { appConfig() }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reports/revenue?from=$from&to=$to&bucket=HOUR").apply {
            assertEquals(HttpStatusCode.BadRequest, status, bodyAsText())
        }
    }

    @Test
    fun `GET revenue 400 when from is not before to`() = testApplication {
        coEvery {
            reportService.getRevenueReport(any(), any(), any())
        } throws BadRequestException("'from' must be before 'to'")

        application { appConfig() }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reports/revenue?from=$to&to=$from").apply {
            assertEquals(HttpStatusCode.BadRequest, status, bodyAsText())
        }
    }

    @Test
    fun `GET capacity 200 with movieId and roomId filters passed through`() = testApplication {
        coEvery {
            reportService.getCapacityReport(from, to, 10, 0L, 7L, 3L)
        } returns CapacityReport(
            from = from, to = to,
            rows = emptyList(),
            totalShowtimes = 0,
            avgOccupancyRate = 0.0,
            totalSeatsBooked = 0,
            totalSeatsTotal = 0,
        )

        application { appConfig() }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reports/capacity?from=$from&to=$to&movieId=7&roomId=3").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
        coVerify { reportService.getCapacityReport(from, to, 10, 0L, 7L, 3L) }
    }

    @Test
    fun `GET capacity 400 on negative offset`() = testApplication {
        application { appConfig() }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reports/capacity?from=$from&to=$to&offset=-1").apply {
            assertEquals(HttpStatusCode.BadRequest, status, bodyAsText())
        }
    }

    @Test
    fun `GET capacity 401 without token`() = testApplication {
        application { appConfig() }
        val client = clientConfiguration()
        client.get("/admin/reports/capacity?from=$from&to=$to").apply {
            assertEquals(HttpStatusCode.Unauthorized, status, bodyAsText())
        }
    }

    private fun Application.appConfig() = testAppConfiguration(reportModule) {
        reportRoute()
    }
}
