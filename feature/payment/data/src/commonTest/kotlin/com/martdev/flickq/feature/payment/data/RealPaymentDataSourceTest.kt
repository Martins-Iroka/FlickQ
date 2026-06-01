package com.martdev.flickq.feature.payment.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import com.martdev.flickq.payment.model.PaymentStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.fail

private fun jsonClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
}

private fun MockRequestHandleScope.jsonOk(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

class RealPaymentDataSourceTest {

    @Test
    fun `initializePayment maps the gateway hand-off to a PENDING payment`() = runTest {
        var path = ""
        var method = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            method = request.method.value
            jsonOk(
                """{"data":{"authorization_url":"https://checkout.paystack.com/abc","access_code":"ac_1",""" +
                    """"reference":"FQ-PAY-000001","reservation_id":7}}""",
                HttpStatusCode.Created
            )
        }

        val payment = (RealPaymentDataSource(client).initializePayment(7) as? Result.Success)?.data
            ?: fail("expected success")
        assertThat(payment.reservationId).isEqualTo(7L)
        assertThat(payment.reference).isEqualTo("FQ-PAY-000001")
        assertThat(payment.authorizationUrl).isEqualTo("https://checkout.paystack.com/abc")
        assertThat(payment.accessCode).isEqualTo("ac_1")
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(method).isEqualTo("POST")
        assertThat(path.endsWith("/payment/initialize")).isEqualTo(true)
    }

    @Test
    fun `verifyPayment hits verify-by-reference and maps the payment row`() = runTest {
        var path = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            jsonOk(
                """{"data":{"id":3,"reservation_id":7,"user_id":1,"reference":"FQ-PAY-000001",""" +
                    """"amount":7000,"currency":"NGN","status":"SUCCESS"}}"""
            )
        }

        val payment = (RealPaymentDataSource(client).verifyPayment("FQ-PAY-000001") as? Result.Success)?.data
            ?: fail("expected success")
        assertThat(payment.amount).isEqualTo(7000L)
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(path.endsWith("/payment/verify/FQ-PAY-000001")).isEqualTo(true)
    }
}
