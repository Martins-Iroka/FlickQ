package com.martdev.flickq.feature.reservation.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import com.martdev.flickq.reservation.ReservationTicketDTO
import com.martdev.flickq.reservation.TicketPaymentDTO
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.shared.DataResponse
import com.martdev.flickq.shared.ErrorResponse
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private fun jsonClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) {
        json(HttpClientFactory.json)
    }
}

private fun MockRequestHandleScope.jsonOk(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

class MobileReservationRepoTest {

    @Test
    fun `getMyReservations hits my-reservations and map list items to domain`() = runTest {
        var path = ""
        val response = DataResponse(
            listOf(ReservationTicketDTO(
                status = ReservationStatus.CONFIRMED.toString(),
                totalAmount = 5_000,
                endsAt = Clock.System.now().plus(2.hours),
                movieTitle = "Apex",
                releasedDate = "2026-08-21",
                roomName = "Room 1",
                seats = "A5",
                posterUrl = "url",
                payment = TicketPaymentDTO(
                    status = "successful",
                    reference = "ref",
                    paidAt = Clock.System.now().minus(10.minutes)
                )
            ))
        )
        val client = jsonClient {request ->
            path = request.url.encodedPath
            jsonOk(Json.encodeToString(response))
        }

        val result = MobileReservationRepoImpl(client).getMyReservationTickets(
            status = ReservationStatus.CONFIRMED,
            limit = 10,
            offset = 0
        )

        val tickets = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(tickets).isNotEmpty()
        val ticket = tickets.single()
        assertThat(ticket.status).isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(ticket.totalAmount).isEqualTo(response.data.single().totalAmount)
        assertThat(ticket.showtimeEndsAt.toLocalDateTime(TimeZone.currentSystemDefault())).isEqualTo(response.data.single().endsAt.toLocalDateTime(TimeZone.currentSystemDefault()))
        assertThat(ticket.payment).isNotNull()
        assertThat(path.endsWith("/reservation/my-reservations")).isEqualTo(true)
    }

    @Test
    fun `getMyReservations forwards status, limit and offset as query parameter`() = runTest {
        var query = ""
        val client = jsonClient { request ->
            query = request.url.encodedQuery
            jsonOk(Json.encodeToString(DataResponse(emptyList<ReservationTicketDTO>())))
        }

        MobileReservationRepoImpl(client).getMyReservationTickets(
            status = ReservationStatus.CONFIRMED,
            10, 0
        )

        assertThat(query.contains("status=CONFIRMED")).isEqualTo(true)
        assertThat(query.contains("limit=10")).isEqualTo(true)
        assertThat(query.contains("offset=0")).isEqualTo(true)
    }

    @Test
    fun `getMyReservations returns bad request for invalid limit parameter`() = runTest {
        val error = ErrorResponse(
            "'limit' and 'offset' must be positive"
        )
        val client = jsonClient {
            respond(content = Json.encodeToString(error), status = HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = MobileReservationRepoImpl(client).getMyReservationTickets(
            ReservationStatus.CONFIRMED, -1, 44
        )
        assertTrue(result is Result.Error)
        assertThat(result.error).isEqualTo(DataError.Network.BAD_REQUEST)
        assertThat(result.message).isEqualTo(error.error)
    }
}