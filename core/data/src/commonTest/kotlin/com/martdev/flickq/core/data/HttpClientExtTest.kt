package com.martdev.flickq.core.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.movie.GenreDTO
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
import kotlin.test.Test
import kotlin.test.fail

// :core:data has no kotlinx-serialization compiler plugin, so this test reuses a real
// :core:api DTO (which carries a generated serializer) rather than declaring a local one.

private fun jsonClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
}

private fun MockRequestHandleScope.jsonOk(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

class HttpClientExtTest {

    @Test
    fun `putData sends the body and unwraps the DataResponse envelope`() = runTest {
        var method = ""
        var sentBody = ""
        val client = jsonClient { request ->
            method = request.method.value
            sentBody = (request.body as? TextContent)?.text.orEmpty()
            jsonOk("""{"data":{"id":7,"name":"Updated"}}""")
        }

        val result = client.putData<GenreDTO, GenreDTO>("/admin/genre/update/7", GenreDTO(7, "Updated"))

        val genre = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(genre).isEqualTo(GenreDTO(7, "Updated"))
        assertThat(method).isEqualTo("PUT")
        assertThat(sentBody.contains("\"name\":\"Updated\"")).isEqualTo(true)
    }

    @Test
    fun `patchData with a body unwraps the envelope`() = runTest {
        var method = ""
        val client = jsonClient { request ->
            method = request.method.value
            jsonOk("""{"data":{"id":3,"name":"Patched"}}""")
        }

        val result = client.patchData<GenreDTO, GenreDTO>("/admin/genre/3", GenreDTO(3, "Patched"))

        assertThat((result as? Result.Success)?.data).isEqualTo(GenreDTO(3, "Patched"))
        assertThat(method).isEqualTo("PATCH")
    }

    @Test
    fun `patchData with no body still PATCHes and unwraps`() = runTest {
        var hadBody = true
        val client = jsonClient { request ->
            hadBody = request.body.contentLength?.let { it > 0 } ?: false
            jsonOk("""{"data":{"id":9,"name":"Cancelled"}}""")
        }

        val result = client.patchData<Unit, GenreDTO>("/admin/reservation/cancel/9")

        assertThat((result as? Result.Success)?.data).isEqualTo(GenreDTO(9, "Cancelled"))
        assertThat(hadBody).isEqualTo(false)
    }

    @Test
    fun `deleteForStatus resolves a 204 to Unit without reading a body`() = runTest {
        var method = ""
        val client = jsonClient { request ->
            method = request.method.value
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NoContent)
        }

        val result = client.deleteForStatus("/admin/movie/delete-movie/5")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(method).isEqualTo("DELETE")
    }

    @Test
    fun `deleteForStatus maps a 409 referenced-resource conflict to CONFLICT`() = runTest {
        val client = jsonClient { jsonOk("", HttpStatusCode.Conflict) }

        val result = client.deleteForStatus("/admin/room/delete-room/2")

        assertThat((result as? Result.Error)?.error).isEqualTo(DataError.Network.CONFLICT)
    }

    @Test
    fun `a 4xx response surfaces the server error message`() = runTest {
        val client = jsonClient { jsonOk("""{"error":"Duplicate email"}""", HttpStatusCode.BadRequest) }

        val result = client.getResult<GenreDTO>("/authentication/register")

        val error = result as? Result.Error ?: fail("expected error, was $result")
        assertThat(error.error).isEqualTo(DataError.Network.BAD_REQUEST)
        assertThat(error.message).isEqualTo("Duplicate email")
    }

    @Test
    fun `a 5xx response keeps a null message so the curated copy wins`() = runTest {
        val client = jsonClient {
            jsonOk("""{"error":"Internal server error"}""", HttpStatusCode.InternalServerError)
        }

        val result = client.getResult<GenreDTO>("/movie/list")

        val error = result as? Result.Error ?: fail("expected error, was $result")
        assertThat(error.error).isEqualTo(DataError.Network.SERVER_ERROR)
        assertThat(error.message).isNull()
    }

    @Test
    fun `a 4xx with a non-ErrorResponse body falls back to a null message`() = runTest {
        val client = jsonClient {
            respond(
                content = "<html>not found</html>",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }

        val result = client.getResult<GenreDTO>("/movie/9999")

        val error = result as? Result.Error ?: fail("expected error, was $result")
        assertThat(error.error).isEqualTo(DataError.Network.NOT_FOUND)
        assertThat(error.message).isNull()
    }

    @Test
    fun `postForStatusNoBody resolves a 201 to Unit and sends no body`() = runTest {
        var method = ""
        var hadBody = true
        val client = jsonClient { request ->
            method = request.method.value
            hadBody = request.body.contentLength?.let { it > 0 } ?: false
            jsonOk("""{"data":"Seats populated successfully"}""", HttpStatusCode.Created)
        }

        val result = client.postForStatusNoBody("/admin/reservation/populate-seats/4")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(method).isEqualTo("POST")
        assertThat(hadBody).isEqualTo(false)
    }
}
