package com.martdev.flickq.feature.movie.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.fail

private fun jsonClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData
): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
}

private fun MockRequestHandleScope.jsonOk(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

class RealMovieDataSourceTest {

    @Test
    fun `getMovies hits get-movies and maps list items to domain`() = runTest {
        var path = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            jsonOk("""{"data":[{"id":1,"title":"Neon Skyline","posterUrl":"neon.jpg"}]}""")
        }

        val result = RealMovieDataSource(client).getMovies()

        val movies = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(movies).hasSize(1)
        assertThat(movies[0].id).isEqualTo(1L)
        assertThat(movies[0].title).isEqualTo("Neon Skyline")
        assertThat(movies[0].posterUrl).isEqualTo("neon.jpg")
        assertThat(path.endsWith("/movie/get-movies")).isEqualTo(true)
    }

    @Test
    fun `getMovieById unwraps DataResponse and parses release date plus genres`() = runTest {
        var path = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            jsonOk(
                """{"data":{"id":3,"title":"Paper Lanterns","description":"d","posterUrl":"p.jpg",""" +
                    """"duration":99,"releasedDate":"2026-01-23","genres":[{"id":4,"name":"Animation"}]}}"""
            )
        }

        val movie = (RealMovieDataSource(client).getMovieById(3) as? Result.Success)?.data
            ?: fail("expected success")
        assertThat(movie.duration).isEqualTo(99)
        assertThat(movie.releasedDate.toString()).isEqualTo("2026-01-23")
        assertThat(movie.genres.single().name).isEqualTo("Animation")
        assertThat(path.endsWith("/movie/get-movie-by-id/3")).isEqualTo(true)
    }

    @Test
    fun `getMovieById maps 404 to NOT_FOUND`() = runTest {
        val client = jsonClient { jsonOk("", HttpStatusCode.NotFound) }

        val result = RealMovieDataSource(client).getMovieById(99)

        val error = (result as? Result.Error)?.error ?: fail("expected error, was $result")
        assertThat(error).isEqualTo(DataError.Network.NOT_FOUND)
    }
}
