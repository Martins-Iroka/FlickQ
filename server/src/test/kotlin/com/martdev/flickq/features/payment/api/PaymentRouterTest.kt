package com.martdev.flickq.features.payment.api

import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.payment.domain.api.paymentRoute
import com.martdev.flickq.features.payment.domain.api.paystackSignatureHeader
import com.martdev.flickq.features.payment.domain.service.InitializePaymentResult
import com.martdev.flickq.features.payment.domain.service.PaymentService
import com.martdev.flickq.payment.InitializePaymentRequest
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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

@ExtendWith(MockKExtension::class)
class PaymentRouterTest {

    @MockK
    private lateinit var paymentService: PaymentService

    private val jwtConfig = JWTConfig("test", 15, "iss", "aud")
    private val jwt = JWTAuthImpl(jwtConfig)
    private val userToken = jwt.generateAccessToken("2", "USER")
    private val adminToken = jwt.generateAccessToken("1", "ADMIN")

    private val paymentModule = module {
        single { paymentService }
        single { jwtConfig }
    }

    @Test
    fun `POST initialize returns 201 with authorization_url`() = testApplication {
        coEvery { paymentService.initializePayment(any(), any()) } returns InitializePaymentResult(
            authorizationUrl = "https://paystack.co/pay/abc",
            accessCode = "code",
            reference = "ref",
            reservationId = 1L,
        )

        application { appConfig() }
        val client = clientConfiguration(userToken)
        client.post("/payment/initialize") {
            setBody(InitializePaymentRequest(reservationId = 1L))
        }.apply {
            assertEquals(HttpStatusCode.Created, status, bodyAsText())
        }
    }

    @Test
    fun `GET verify with reference returns 200`() = testApplication {
        coEvery { paymentService.verifyPayment("ref_xyz", requestingUserId = 2L) } returns Payment(
            reference = "ref_xyz",
            status = PaymentStatus.SUCCESS,
        )

        application { appConfig() }
        val client = clientConfiguration(userToken)
        client.get("/payment/verify/ref_xyz").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun `POST webhook forwards raw body and signature header to service`() = testApplication {
        coJustRun { paymentService.handleWebhook(any(), any()) }

        application { appConfig() }
        val client = clientConfiguration()
        val body = """{"event":"charge.success","data":{}}"""
        client.post("/payment/webhook") {
            header(paystackSignatureHeader, "abc123")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }.apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
        coVerify { paymentService.handleWebhook(body, "abc123") }
    }

    @Test
    fun `GET callback verifies by reference without auth`() = testApplication {
        coEvery { paymentService.verifyPayment("ref_xyz", requestingUserId = null) } returns Payment(
            reference = "ref_xyz",
            reservationId = 1L,
            status = PaymentStatus.SUCCESS,
        )

        application { appConfig() }
        val client = clientConfiguration() // no token
        client.get("/payment/callback?reference=ref_xyz").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun `GET admin payment by-reservation rejects non-admin`() = testApplication {
        application { appConfig() }
        val client = clientConfiguration(userToken)
        client.get("/admin/payment/by-reservation/1").apply {
            assertEquals(HttpStatusCode.Forbidden, status, bodyAsText())
        }
    }

    @Test
    fun `GET admin payment by-reservation returns list for admin`() = testApplication {
        coEvery { paymentService.getPaymentsByReservationId(1L) } returns listOf(Payment())

        application { appConfig() }
        val client = clientConfiguration(adminToken)
        client.get("/admin/payment/by-reservation/1").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    private fun Application.appConfig() = testAppConfiguration(paymentModule) {
        paymentRoute()
    }
}
