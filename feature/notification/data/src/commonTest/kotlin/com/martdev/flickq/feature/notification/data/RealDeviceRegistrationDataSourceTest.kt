package com.martdev.flickq.feature.notification.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import com.martdev.flickq.notification.model.DevicePlatform
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

private fun jsonClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}

private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

// Matched by class name in networkErrorFor, like the platform "unresolved address" exceptions.
private class FakeUnresolvedAddressException : Exception()

private val appInfo = AppInfo(platform = DevicePlatform.ANDROID, appVersion = "1.4.0", locale = "en-NG")

private fun source(client: HttpClient) = RealDeviceRegistrationDataSource(client, DevicePlatform.ANDROID, appInfo)

class RealDeviceRegistrationDataSourceTest {

    @Test
    fun `register PUTs the id with platform version and locale and accepts a 204`() = runTest {
        var method = ""
        var path = ""
        var body = ""
        val client = jsonClient { request ->
            method = request.method.value
            path = request.url.encodedPath
            body = (request.body as? TextContent)?.text.orEmpty()
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NoContent)
        }

        val result = source(client).register("fid-1")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(method).isEqualTo("PUT")
        assertThat(path.endsWith("/devices")).isEqualTo(true)
        val json = Json.parseToJsonElement(body).jsonObject
        assertThat(json["token"]?.jsonPrimitive?.content).isEqualTo("fid-1")
        assertThat(json["platform"]?.jsonPrimitive?.content).isEqualTo("ANDROID")
        assertThat(json["appVersion"]?.jsonPrimitive?.content).isEqualTo("1.4.0")
        assertThat(json["locale"]?.jsonPrimitive?.content).isEqualTo("en-NG")
    }

    @Test
    fun `register maps server errors`() = runTest {
        mapOf(
            HttpStatusCode.BadRequest to DataError.Network.BAD_REQUEST,
            HttpStatusCode.Forbidden to DataError.Network.FORBIDDEN,
            HttpStatusCode.TooManyRequests to DataError.Network.TOO_MANY_REQUESTS,
            HttpStatusCode.InternalServerError to DataError.Network.SERVER_ERROR,
        ).forEach { (status, expected) ->
            val client = jsonClient { respondJson("""{"error":"nope"}""", status) }

            val result = source(client).register("fid-1")

            assertThat((result as? Result.Error)?.error, name = status.toString()).isEqualTo(expected)
        }
    }

    @Test
    fun `register maps a connectivity failure to NO_INTERNET`() = runTest {
        val client = jsonClient { throw FakeUnresolvedAddressException() }

        val result = source(client).register("fid-1")

        assertThat((result as? Result.Error)?.error).isEqualTo(DataError.Network.NO_INTERNET)
    }

    @Test
    fun `unregister DELETEs the path-encoded id`() = runTest {
        var method = ""
        var path = ""
        val client = jsonClient { request ->
            method = request.method.value
            path = request.url.encodedPath
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NoContent)
        }

        val result = source(client).unregister("fid/with space")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(method).isEqualTo("DELETE")
        assertThat(path.endsWith("/devices/fid%2Fwith%20space")).isEqualTo(true)
    }
}