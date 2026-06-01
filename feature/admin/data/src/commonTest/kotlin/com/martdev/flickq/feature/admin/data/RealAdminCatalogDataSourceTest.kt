package com.martdev.flickq.feature.admin.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.showtime.model.Showtime
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.fail

private class Recorder {
    var method: String = ""
    var path: String = ""
    var query: String = ""
    var body: String = ""
}

private fun clientRecording(
    recorder: Recorder,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine { request ->
    recorder.method = request.method.value
    recorder.path = request.url.encodedPath
    recorder.query = request.url.encodedQuery
    recorder.body = (request.body as? TextContent)?.text.orEmpty()
    handler(request)
}) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
}

private fun MockRequestHandleScope.jsonOk(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

class RealAdminCatalogDataSourceTest {

    @Test
    fun `getMovies maps the list-item envelope and sends pagination`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) {
            jsonOk("""{"data":[{"id":1,"title":"Neon","posterUrl":"p1"},{"id":2,"title":"Coast","posterUrl":"p2"}]}""")
        }

        val result = RealAdminCatalogDataSource(client).getMovies(limit = 25, offset = 5)

        val movies = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(movies).hasSize(2)
        assertThat(movies[0].title).isEqualTo("Neon")
        assertThat(rec.path.endsWith("/movie/get-movies")).isEqualTo(true)
        assertThat(rec.query.contains("limit=25")).isEqualTo(true)
        assertThat(rec.query.contains("offset=5")).isEqualTo(true)
    }

    @Test
    fun `createMovie POSTs MovieDTO and resolves a bodyless 201 to Unit`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) { respond(ByteReadChannel.Empty, HttpStatusCode.Created) }

        val result = RealAdminCatalogDataSource(client)
            .createMovie(Movie(id = 0, title = "New Film", releasedDate = LocalDate(2026, 6, 1)))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(rec.method).isEqualTo("POST")
        assertThat(rec.path.endsWith("/admin/movie/create-movie")).isEqualTo(true)
        assertThat(rec.body.contains("\"title\":\"New Film\"")).isEqualTo(true)
        // LocalDate serialises to the wire as an ISO date string the server can parse.
        assertThat(rec.body.contains("2026-06-01")).isEqualTo(true)
    }

    @Test
    fun `updateMovie PUTs to the id path and unwraps the returned movie`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) {
            jsonOk("""{"data":{"id":7,"title":"Edited","description":"d","posterUrl":"","duration":100,"releasedDate":"2026-02-02","genres":[]}}""")
        }

        val result = RealAdminCatalogDataSource(client).updateMovie(Movie(id = 7, title = "Edited"))

        val movie = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(movie.title).isEqualTo("Edited")
        assertThat(rec.method).isEqualTo("PUT")
        assertThat(rec.path.endsWith("/admin/movie/update-movie/7")).isEqualTo(true)
    }

    @Test
    fun `deleteMovie DELETEs the id path and a 409 maps to CONFLICT`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) { jsonOk("", HttpStatusCode.Conflict) }

        val result = RealAdminCatalogDataSource(client).deleteMovie(7)

        assertThat((result as? Result.Error)?.error).isEqualTo(DataError.Network.CONFLICT)
        assertThat(rec.method).isEqualTo("DELETE")
        assertThat(rec.path.endsWith("/admin/movie/delete-movie/7")).isEqualTo(true)
    }

    @Test
    fun `createGenre POSTs to the admin genre route`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) { respond(ByteReadChannel.Empty, HttpStatusCode.Created) }

        val result = RealAdminCatalogDataSource(client).createGenre(Genre(0, "Horror"))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(rec.path.endsWith("/admin/genre/create-genre")).isEqualTo(true)
        assertThat(rec.body.contains("Horror")).isEqualTo(true)
    }

    @Test
    fun `createRoom unwraps the created room envelope`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) {
            jsonOk("""{"data":{"id":9,"name":"Screen 9","rows":8,"columns":10}}""", HttpStatusCode.Created)
        }

        val result = RealAdminCatalogDataSource(client).createRoom(Room(0, "Screen 9", 8, 10))

        val room = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(room.id).isEqualTo(9L)
        assertThat(rec.path.endsWith("/admin/room/create-room")).isEqualTo(true)
    }

    @Test
    fun `createSeats POSTs a list with snake_cased fields and unwraps the result`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) {
            jsonOk("""{"data":[{"id":50,"room_id":3,"row_label":"A","seat_number":1}]}""", HttpStatusCode.Created)
        }

        val result = RealAdminCatalogDataSource(client)
            .createSeats(listOf(Seat(0, roomId = 3, rowLabel = "A", seatNumber = 1)))

        val seats = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(seats).hasSize(1)
        assertThat(seats[0].id).isEqualTo(50L)
        assertThat(rec.path.endsWith("/admin/seat/create-seats")).isEqualTo(true)
        assertThat(rec.body.contains("\"room_id\":3")).isEqualTo(true)
        assertThat(rec.body.contains("\"row_label\":\"A\"")).isEqualTo(true)
    }

    @Test
    fun `getShowtimes hits the admin route and maps an unknown status defensively`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) {
            jsonOk("""{"data":[{"id":1,"movieId":1,"roomId":1,"startsAt":"2026-06-01T18:00:00Z","endsAt":"2026-06-01T20:00:00Z","price":3500,"status":"WEIRD"}]}""")
        }

        val result = RealAdminCatalogDataSource(client).getShowtimes()

        val showtimes = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(showtimes[0].status).isEqualTo(ShowtimeStatus.SCHEDULED)
        assertThat(rec.path.endsWith("/admin/showtime/get-showtimes")).isEqualTo(true)
    }

    @Test
    fun `updateShowtimeStatus PATCHes the status request and unwraps the showtime`() = runTest {
        val rec = Recorder()
        val client = clientRecording(rec) {
            jsonOk("""{"data":{"id":1,"movieId":1,"roomId":1,"startsAt":"2026-06-01T18:00:00Z","endsAt":"2026-06-01T20:00:00Z","price":3500,"status":"CANCELLED"}}""")
        }

        val result = RealAdminCatalogDataSource(client).updateShowtimeStatus(1, ShowtimeStatus.CANCELLED)

        val showtime = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(showtime.status).isEqualTo(ShowtimeStatus.CANCELLED)
        assertThat(rec.method).isEqualTo("PATCH")
        assertThat(rec.path.endsWith("/admin/showtime/update-showtime-status/1")).isEqualTo(true)
        assertThat(rec.body.contains("CANCELLED")).isEqualTo(true)
    }
}
