package com.martdev.flickq.features.payment.infrastructure.paystack

import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.infrastruce.http.KtorHttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PaystackHttpClientTest {

    private val config = PaystackConfig(
        secretKey = "sk_test_abc",
        publicKey = "pk_test_xyz",
        baseUrl = "https://api.paystack.test",
        callbackUrl = "https://example.test/callback",
        webhookSecret = "sk_test_abc",
        currency = "NGN",
    )

    private val httpClientFactory = KtorHttpClientFactory()

    @Test
    fun `initializeTransaction sends bearer token and parses response`() = runTest {
        var capturedAuth: String? = null
        val engine = MockEngine { req ->
            capturedAuth = req.headers[HttpHeaders.Authorization]
            respond(
                content = ByteReadChannel(
                    """{"status":true,"message":"OK","data":{"authorization_url":"https://paystack/pay","access_code":"ac","reference":"ref_xyz"}}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = PaystackHttpClient(config, httpClientFactory, engine)

        val response = client.initializeTransaction(
            email = "u@x.io",
            amount = 500000L,
        )

        assertEquals("Bearer sk_test_abc", capturedAuth)
        val data = assertNotNull(response.data)
        assertEquals("https://paystack/pay", data.authorizationUrl)
        assertEquals("ref_xyz", data.reference)
    }

    @Test
    fun `verifyTransaction parses success payload`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(
                    """{"status":true,"message":"Verification successful","data":{"id":7,"status":"success","reference":"ref_xyz","amount":500000,"currency":"NGN","paid_at":"2026-05-19T12:00:00Z","gateway_response":"Approved"}}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = PaystackHttpClient(config, httpClientFactory, engine)

        val response = client.verifyTransaction("ref_xyz")

        assertTrue(response.status)
        assertEquals("success", response.data?.status)
        assertEquals(500000L, response.data?.amount)
    }

    @Test
    fun `4xx response surfaces as BadRequestException`() = runTest {
        val engine = MockEngine { _ ->
            respondError(
                status = HttpStatusCode.BadRequest,
                content = """{"status":false,"message":"Invalid key"}""",
            )
        }
        val client = PaystackHttpClient(config, httpClientFactory, engine)

        assertThrows<BadRequestException> {
            client.verifyTransaction("ref_xyz")
        }
    }
}
