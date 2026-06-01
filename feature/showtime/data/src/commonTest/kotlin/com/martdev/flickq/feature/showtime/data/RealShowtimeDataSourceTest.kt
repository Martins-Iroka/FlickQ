package com.martdev.flickq.feature.showtime.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import com.martdev.flickq.showtime.model.ShowtimeStatus
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

private fun MockRequestHandleScope.jsonOk(body: String) =
    respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))

class RealShowtimeDataSourceTest {

    @Test
    fun `getShowtimesByMovieId maps list and known status`() = runTest {
        var path = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            jsonOk(
                """{"data":[{"id":7,"movieId":1,"roomId":2,"startsAt":"2026-06-02T13:00:00Z",""" +
                    """"endsAt":"2026-06-02T15:00:00Z","price":3500,"status":"SCHEDULED"}]}"""
            )
        }

        val showtimes = (RealShowtimeDataSource(client).getShowtimesByMovieId(1) as? Result.Success)?.data
            ?: fail("expected success")
        assertThat(showtimes).hasSize(1)
        assertThat(showtimes[0].id).isEqualTo(7L)
        assertThat(showtimes[0].price).isEqualTo(3500)
        assertThat(showtimes[0].status).isEqualTo(ShowtimeStatus.SCHEDULED)
        assertThat(path.endsWith("/showtime/get-showtimes-by-movie-id/1")).isEqualTo(true)
    }

    @Test
    fun `getShowtimeById maps an unknown status to CANCELLED defensively`() = runTest {
        val client = jsonClient {
            jsonOk("""{"data":{"id":9,"movieId":1,"roomId":2,"price":3000,"status":"ARCHIVED"}}""")
        }

        val showtime = (RealShowtimeDataSource(client).getShowtimeById(9) as? Result.Success)?.data
            ?: fail("expected success")
        assertThat(showtime.status).isEqualTo(ShowtimeStatus.CANCELLED)
    }
}
