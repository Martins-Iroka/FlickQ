package com.martdev.flickq.feature.admin.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.SeatStatus
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

private fun recordingClient(
    onRequest: (HttpRequestData) -> Unit,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine { request -> onRequest(request); handler(request) }) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
}

private fun MockRequestHandleScope.jsonOk(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

private const val RESERVATION_JSON =
    """{"id":5,"user_id":11,"showtime_id":1,"status":"CONFIRMED","total_amount":7000,""" +
        """"seats":[{"id":1,"showtime_id":1,"seat_id":101,"reservation_id":5,"status":"BOOKED"}],""" +
        """"created_at":"2026-06-01T10:00:00Z","expires_at":"2026-06-01T10:15:00Z"}"""

class RealAdminReservationDataSourceTest {

    @Test
    fun `getReservations maps the snake_cased envelope and paginates`() = runTest {
        var path = ""
        var query = ""
        val client = recordingClient({ path = it.url.encodedPath; query = it.url.encodedQuery }) {
            jsonOk("""{"data":[$RESERVATION_JSON]}""")
        }

        val result = RealAdminReservationDataSource(client).getReservations(limit = 10, offset = 0)

        val reservations = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(reservations).hasSize(1)
        assertThat(reservations[0].userId).isEqualTo(11L)
        assertThat(reservations[0].status).isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(reservations[0].seats[0].status).isEqualTo(SeatStatus.BOOKED)
        assertThat(path.endsWith("/admin/reservation/get-all")).isEqualTo(true)
        assertThat(query.contains("limit=10")).isEqualTo(true)
    }

    @Test
    fun `cancelReservation PATCHes the cancel path with no body and returns the cancelled reservation`() = runTest {
        var method = ""
        var path = ""
        var hadBody = true
        val client = recordingClient({
            method = it.method.value
            path = it.url.encodedPath
            hadBody = it.body.contentLength?.let { len -> len > 0 } ?: false
        }) {
            jsonOk("""{"data":${RESERVATION_JSON.replace("CONFIRMED", "CANCELLED")}}""")
        }

        val result = RealAdminReservationDataSource(client).cancelReservation(5)

        val reservation = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELLED)
        assertThat(method).isEqualTo("PATCH")
        assertThat(path.endsWith("/admin/reservation/cancel/5")).isEqualTo(true)
        assertThat(hadBody).isEqualTo(false)
    }

    @Test
    fun `populateSeats POSTs the showtime path with no body and resolves to Unit`() = runTest {
        var method = ""
        var path = ""
        val client = recordingClient({ method = it.method.value; path = it.url.encodedPath }) {
            jsonOk("""{"data":"Seats populated successfully"}""", HttpStatusCode.Created)
        }

        val result = RealAdminReservationDataSource(client).populateSeats(3)

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(method).isEqualTo("POST")
        assertThat(path.endsWith("/admin/reservation/populate-seats/3")).isEqualTo(true)
    }

    @Test
    fun `getPaymentsByReservation maps the payment list envelope`() = runTest {
        var path = ""
        val client = recordingClient({ path = it.url.encodedPath }) {
            jsonOk(
                """{"data":[{"id":1,"reservation_id":5,"user_id":11,"reference":"FQ-1","amount":7000,""" +
                    """"currency":"NGN","status":"SUCCESS","paid_at":"2026-06-01T10:05:00Z"}]}"""
            )
        }

        val result = RealAdminPaymentDataSource(client).getPaymentsByReservation(5)

        val payments = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(payments).hasSize(1)
        assertThat(payments[0].reference).isEqualTo("FQ-1")
        assertThat(payments[0].status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(path.endsWith("/admin/payment/by-reservation/5")).isEqualTo(true)
    }
}
