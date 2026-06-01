package com.martdev.flickq.feature.booking.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
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

private fun jsonClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
}

private fun MockRequestHandleScope.jsonOk(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

class RealBookingDataSourceTest {

    @Test
    fun `getSeatMap assembles four calls and marks seats absent from available as booked`() = runTest {
        val client = jsonClient { request ->
            when (val path = request.url.encodedPath) {
                "/api/v1/showtime/get-showtime-by-id/10" ->
                    jsonOk("""{"data":{"id":10,"movieId":1,"roomId":5,"price":3000,"status":"SCHEDULED"}}""")
                "/api/v1/room/get-room-by-id/5" ->
                    jsonOk("""{"data":{"id":5,"name":"Room A","rows":1,"columns":2}}""")
                "/api/v1/seat/get-seats-by-room-id/5" ->
                    jsonOk(
                        """{"data":[{"id":100,"room_id":5,"row_label":"A","seat_number":1},""" +
                            """{"id":101,"room_id":5,"row_label":"A","seat_number":2}]}"""
                    )
                "/api/v1/reservation/available-seats/10" ->
                    jsonOk("""{"data":[{"id":1,"showtime_id":10,"seat_id":100,"status":"AVAILABLE"}]}""")
                else -> fail("unexpected path $path")
            }
        }

        val seatMap = (RealBookingDataSource(client).getSeatMap(10) as? Result.Success)?.data
            ?: fail("expected success")
        assertThat(seatMap.rows).isEqualTo(1)
        assertThat(seatMap.columns).isEqualTo(2)
        assertThat(seatMap.seatPrice).isEqualTo(3000)
        assertThat(seatMap.seats).hasSize(2)
        assertThat(seatMap.seats.first { it.seat.id == 100L }.status).isEqualTo(SeatStatus.AVAILABLE)
        assertThat(seatMap.seats.first { it.seat.id == 101L }.status).isEqualTo(SeatStatus.BOOKED)
    }

    @Test
    fun `getSeatMap propagates an error from any leg`() = runTest {
        val client = jsonClient { jsonOk("", HttpStatusCode.NotFound) }

        val result = RealBookingDataSource(client).getSeatMap(10)

        val error = (result as? Result.Error)?.error ?: fail("expected error, was $result")
        assertThat(error).isEqualTo(DataError.Network.NOT_FOUND)
    }

    @Test
    fun `createReservation posts to create and maps the reservation`() = runTest {
        var path = ""
        var method = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            method = request.method.value
            jsonOk(
                """{"data":{"id":42,"user_id":1,"showtime_id":10,"status":"PENDING","total_amount":7000,""" +
                    """"seats":[{"id":1,"showtime_id":10,"seat_id":100,"reservation_id":42,"status":"BOOKED"}]}}""",
                HttpStatusCode.Created
            )
        }

        val reservation = (RealBookingDataSource(client).createReservation(10, listOf(100)) as? Result.Success)?.data
            ?: fail("expected success")
        assertThat(reservation.id).isEqualTo(42L)
        assertThat(reservation.status).isEqualTo(ReservationStatus.PENDING)
        assertThat(reservation.totalAmount).isEqualTo(7000L)
        assertThat(reservation.seats.single().seatId).isEqualTo(100L)
        assertThat(method).isEqualTo("POST")
        assertThat(path.endsWith("/reservation/create")).isEqualTo(true)
    }

    @Test
    fun `createReservation rejects an empty seat selection without a network call`() = runTest {
        val client = jsonClient { fail("should not call the network") }

        val result = RealBookingDataSource(client).createReservation(10, emptyList())

        val error = (result as? Result.Error)?.error ?: fail("expected error")
        assertThat(error).isEqualTo(DataError.Network.BAD_REQUEST)
    }
}
