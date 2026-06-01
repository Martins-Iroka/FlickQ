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
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
        return Result.Error(networkErrorFor(e))
    }
    return if (response.status.value in 200..299) Result.Success(Unit) else responseToResult(response)
}

/**
 * POSTs to [route] with **no request body** and resolves to [Unit] on any 2xx without reading
 * the response body — for body-less admin actions whose success payload is irrelevant (e.g.
 * `admin/reservation/populate-seats/{id}`). Non-2xx maps through [responseToResult].
 */
suspend fun HttpClient.postForStatusNoBody(
    route: String
): Result<Unit, DataError.Network> {
    val response = try {
        post { url(constructRoute(route)) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: ConnectTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: SocketTimeoutException) {
        return Result.Error(DataError.Network.REQUEST_TIMEOUT)
    } catch (e: Exception) {
        return Result.Error(networkErrorFor(e))
    }
    return if (response.status.value in 200..299) Result.Success(Unit) else responseToResult(response)
}

/** PUTs [body] and unwraps the `DataResponse<Response>` envelope (e.g. `admin/movie/update-movie/{id}`). */
suspend inline fun <reified Request, reified Response : Any> HttpClient.putData(
    route: String,
    body: Request
): Result<Response, DataError.Network> =
    safeCall<DataResponse<Response>> {
        put {
            url(constructRoute(route))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.map { it.data }

/**
 * PATCHes and unwraps the `DataResponse<Response>` envelope. [body] is optional — many admin
 * PATCHes carry no payload (e.g. `admin/reservation/cancel/{id}`), where only the path matters
 * and the server returns the mutated resource.
 */
suspend inline fun <reified Request, reified Response : Any> HttpClient.patchData(
    route: String,
    body: Request? = null
): Result<Response, DataError.Network> =
    safeCall<DataResponse<Response>> {
        patch {
            url(constructRoute(route))
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }.map { it.data }

/**
 * DELETEs [route] and resolves to [Unit] on any 2xx (the admin deletes return **204 No Content**)
 * without reading a body. Non-2xx maps through [responseToResult] — notably a 409 when the
 * resource is still referenced (a movie/room/showtime in use) surfaces as [DataError.Network.CONFLICT].
 */
suspend fun HttpClient.deleteForStatus(
    route: String,
    queryParameters: Map<String, Any?> = emptyMap()
): Result<Unit, DataError.Network> {
    val response = try {
        delete {
            url(constructRoute(route))
            queryParameters.forEach { (key, value) -> parameter(key, value) }
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
        return Result.Error(networkErrorFor(e))
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
        return Result.Error(networkErrorFor(e))
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

/**
 * Connectivity failures (DNS/unresolved host/refused) surface as platform-specific exceptions
 * with no common supertype; match by class name so "offline" reads as [DataError.Network.NO_INTERNET]
 * rather than a generic [DataError.Network.UNKNOWN].
 */
fun networkErrorFor(e: Exception): DataError.Network {
    val name = e::class.simpleName.orEmpty() + (e.cause?.let { it::class.simpleName.orEmpty() } ?: "")
    val offline = listOf("UnresolvedAddress", "UnknownHost", "ConnectException", "Network")
        .any { name.contains(it, ignoreCase = true) }
    return if (offline) DataError.Network.NO_INTERNET else DataError.Network.UNKNOWN
}

fun constructRoute(route: String): String = when {
    route.contains(AppConfig.BASE_URL) -> route
    route.startsWith("/") -> "${AppConfig.BASE_URL}$route"
    else -> "${AppConfig.BASE_URL}/$route"
}
