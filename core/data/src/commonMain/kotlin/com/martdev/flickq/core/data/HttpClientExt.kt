package com.martdev.flickq.core.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.shared.DataResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

suspend inline fun <reified Response : Any> HttpClient.getResult(
    route: String,
    queryParameters: Map<String, Any?> = emptyMap()
): Result<Response, DataError.Network> = safeCall {
    get {
        url(constructRoute(route))
        queryParameters.forEach { (key, value) -> parameter(key, value) }
    }
}

suspend inline fun <reified Request, reified Response : Any> HttpClient.postResult(
    route: String,
    body: Request
): Result<Response, DataError.Network> = safeCall {
    post {
        url(constructRoute(route))
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}

suspend inline fun <reified Response : Any> HttpClient.deleteResult(
    route: String,
    queryParameters: Map<String, Any?> = emptyMap()
): Result<Response, DataError.Network> = safeCall {
    delete {
        url(constructRoute(route))
        queryParameters.forEach { (key, value) -> parameter(key, value) }
    }
}

// --- DataResponse helpers ------------------------------------------------------------
// The backend wraps every success body in `{ "data": ... }` (see [DataResponse]). These
// request the envelope and unwrap `.data`, so real data sources deal in payloads directly.

suspend inline fun <reified Response : Any> HttpClient.getData(
    route: String,
    queryParameters: Map<String, Any?> = emptyMap()
): Result<Response, DataError.Network> =
    getResult<DataResponse<Response>>(route, queryParameters).map { it.data }

suspend inline fun <reified Request, reified Response : Any> HttpClient.postData(
    route: String,
    body: Request
): Result<Response, DataError.Network> =
    postResult<Request, DataResponse<Response>>(route, body).map { it.data }

/**
 * POSTs [body] and resolves to [Unit] on any 2xx **without reading the response body** —
 * for endpoints whose success is an empty 200 (e.g. `verify-user`, which only activates the
 * account). Non-2xx maps through [responseToResult]; transport failures map like [safeCall].
 */
suspend inline fun <reified Request> HttpClient.postForStatus(
    route: String,
    body: Request
): Result<Unit, DataError.Network> {
    val response = try {
        post {
            url(constructRoute(route))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: ConnectTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: SocketTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: Exception) {
        val name = e::class.simpleName.orEmpty() + (e.cause?.let { it::class.simpleName.orEmpty() } ?: "")
        val offline = listOf("UnresolvedAddress", "UnknownHost", "ConnectException", "Network")
            .any { name.contains(it, ignoreCase = true) }
        return Result.Error(if (offline) DataError.Network.NO_INTERNET else DataError.Network.UNKNOWN)
    }
    return if (response.status.value in 200..299) Result.Success(Unit) else responseToResult(response)
}

suspend inline fun <reified T> safeCall(
    execute: () -> HttpResponse
): Result<T, DataError.Network> {
    val response = try {
        execute()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: ConnectTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: SocketTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: SerializationException) {
        return Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: Exception) {
        // Connectivity failures (DNS/unresolved host/refused) surface as platform-specific
        // exceptions with no common supertype; match by name so "offline" reads as
        // NO_INTERNET rather than a generic UNKNOWN.
        val name = e::class.simpleName.orEmpty() + (e.cause?.let { it::class.simpleName.orEmpty() } ?: "")
        val offline = listOf("UnresolvedAddress", "UnknownHost", "ConnectException", "Network")
            .any { name.contains(it, ignoreCase = true) }
        return Result.Error(if (offline) DataError.Network.NO_INTERNET else DataError.Network.UNKNOWN)
    }
    return responseToResult(response)
}

suspend inline fun <reified T> responseToResult(
    response: HttpResponse
): Result<T, DataError.Network> {
    return when (response.status.value) {
        in 200..299 -> try {
            Result.Success(response.body<T>())
        } catch (e: SerializationException) {
            Result.Error(DataError.Network.SERIALIZATION)
        }
        400 -> Result.Error(DataError.Network.BAD_REQUEST)
        401 -> Result.Error(DataError.Network.UNAUTHORIZED)
        403 -> Result.Error(DataError.Network.FORBIDDEN)
        404 -> Result.Error(DataError.Network.NOT_FOUND)
        408 -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
        409 -> Result.Error(DataError.Network.CONFLICT)
        413 -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
        429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
        else -> Result.Error(DataError.Network.UNKNOWN)
    }
}

fun constructRoute(route: String): String = when {
    route.contains(AppConfig.BASE_URL) -> route
    route.startsWith("/") -> "${AppConfig.BASE_URL}$route"
    else -> "${AppConfig.BASE_URL}/$route"
}
