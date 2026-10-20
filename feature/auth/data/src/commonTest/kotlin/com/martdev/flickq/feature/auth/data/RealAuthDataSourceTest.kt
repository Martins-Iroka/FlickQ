package com.martdev.flickq.feature.auth.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.HttpClientFactory
import com.martdev.flickq.core.data.InMemoryTokenStorage
import com.martdev.flickq.core.data.TokenStorage
import com.martdev.flickq.core.data.push.DeviceRegistrationStore
import com.martdev.flickq.core.data.push.InMemorySecurePreferences
import com.martdev.flickq.core.data.push.PushInstallationIdHolder
import com.martdev.flickq.feature.auth.domain.AuthError
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Instant

private fun jsonClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient = HttpClient(MockEngine(handler)) {
    install(ContentNegotiation) { json(HttpClientFactory.json) }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}

private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
    respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

private fun jwtWith(payload: String): String {
    val encoded =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray())
    return "header.$encoded.signature"
}

private fun authSource(
    client: HttpClient,
    tokenStorage: TokenStorage,
    installationIds: PushInstallationIdHolder = PushInstallationIdHolder(InMemorySecurePreferences()),
    registrationStore: DeviceRegistrationStore = DeviceRegistrationStore(InMemorySecurePreferences()),
) = RealAuthDataSource(client, tokenStorage, installationIds, registrationStore)

class RealAuthDataSourceTest {

    @Test
    fun `register maps CreateUserResponse to a RegistrationResult`() = runTest {
        var path = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            respondJson(
                """{"data":{"email_id":"fan@flickq.com","token":"reg-tok"}}""",
                HttpStatusCode.Created
            )
        }

        val result = authSource(client, InMemoryTokenStorage())
            .register(Credentials("fan@flickq.com", "secret1"))

        val registration =
            (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(registration.emailId).isEqualTo("fan@flickq.com")
        assertThat(registration.registrationToken).isEqualTo("reg-tok")
        assertThat(path.endsWith("/authentication/register")).isTrue()
    }

    @Test
    fun `register maps a 400 to EMAIL_ALREADY_REGISTERED`() = runTest {
        val client =
            jsonClient { respondJson("""{"error":"duplicate"}""", HttpStatusCode.BadRequest) }

        val result = authSource(client, InMemoryTokenStorage())
            .register(Credentials("dupe@flickq.com", "secret1"))

        assertThat((result as? Result.Error)?.error).isEqualTo(AuthError.EMAIL_ALREADY_REGISTERED)
    }

    @Test
    fun `verifyOtp treats an empty 200 as success without reading a body`() = runTest {
        var path = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.OK)
        }

        val result = authSource(client, InMemoryTokenStorage())
            .verifyOtp(
                VerificationInput(
                    code = "123456",
                    emailId = "fan@flickq.com",
                    registrationToken = "reg-tok"
                )
            )

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(path.endsWith("/authentication/verify-user")).isTrue()
    }

    @Test
    fun `verifyOtp maps a 400 to INVALID_OTP`() = runTest {
        val client =
            jsonClient { respondJson("""{"error":"bad otp"}""", HttpStatusCode.BadRequest) }

        val result = authSource(client, InMemoryTokenStorage())
            .verifyOtp(
                VerificationInput(
                    code = "000000",
                    emailId = "fan@flickq.com",
                    registrationToken = "reg-tok"
                )
            )

        assertThat((result as? Result.Error)?.error).isEqualTo(AuthError.INVALID_OTP)
    }

    @Test
    fun `login persists tokens and reads the user id from the JWT`() = runTest {
        val token = jwtWith("""{"userId":"42","role":"USER"}""")
        val storage = InMemoryTokenStorage()
        val client = jsonClient {
            respondJson(
                """{"data":{"access_token":"$token","refresh_token":"refresh-99"}}""",
                HttpStatusCode.OK
            )
        }

        val result = authSource(client, storage).login(Credentials("fan@flickq.com", "secret1"))

        val login = (result as? Result.Success)?.data ?: fail("expected success, was $result")
        assertThat(login.userId).isEqualTo(42L)
        assertThat(login.refreshToken).isEqualTo("refresh-99")
        assertThat(storage.getAccessToken()).isEqualTo(token)
        assertThat(storage.getRefreshToken()).isEqualTo("refresh-99")
    }

    @Test
    fun `login maps a 401 to INVALID_CREDENTIALS`() = runTest {
        val client = jsonClient { respondJson("""{"error":"nope"}""", HttpStatusCode.Unauthorized) }

        val result = authSource(client, InMemoryTokenStorage())
            .login(Credentials("fan@flickq.com", "wrong"))

        assertThat((result as? Result.Error)?.error).isEqualTo(AuthError.INVALID_CREDENTIALS)
    }

    @Test
    fun `logout posts the stored refresh token on native and clears local tokens`() = runTest {
        var path = ""
        var body = ""
        val storage = InMemoryTokenStorage().apply { saveTokens("access-1", "refresh-1") }
        val client = jsonClient { request ->
            path = request.url.encodedPath
            body = (request.body as? TextContent)?.text.orEmpty()
            respond(content = "", status = HttpStatusCode.OK)
        }

        val result = authSource(client, storage).logout()

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(path.endsWith("/authentication/logout")).isTrue()
        assertThat(body.contains("refresh-1")).isTrue()
        assertThat(storage.getAccessToken()).isNull()
        assertThat(storage.getRefreshToken()).isNull()
    }

    @Test
    fun `logout clears local tokens even when the server call fails`() = runTest {
        val storage = InMemoryTokenStorage().apply { saveTokens("access-1", "refresh-1") }
        val client =
            jsonClient { respond(content = "", status = HttpStatusCode.InternalServerError) }

        authSource(client, storage).logout()

        assertThat(storage.getAccessToken()).isNull()
        assertThat(storage.getRefreshToken()).isNull()
    }

    @Test
    fun `logout with no stored token still hits the endpoint via the web cookie flow`() = runTest {
        var path = ""
        val client = jsonClient { request ->
            path = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.OK)
        }

        authSource(client, InMemoryTokenStorage()).logout()

        assertThat(path.endsWith("/authentication/logout")).isTrue()
    }

    @Test
    fun `logout unregisters the device first then sends the id with the refresh token`() = runTest {
        val calls = mutableListOf<String>()
        var logoutBody = ""
        val storage = InMemoryTokenStorage().apply { saveTokens("access-1", "refresh-1") }
        val installationIds =
            PushInstallationIdHolder(InMemorySecurePreferences()).apply { onRegistered("fid-1") }
        val client = jsonClient { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            if (request.url.encodedPath.endsWith("/authentication/logout")) {
                logoutBody = (request.body as? TextContent)?.text.orEmpty()
            }
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        authSource(client, storage, installationIds).logout()

        assertThat(calls.size).isEqualTo(2)
        assertThat(calls[0].startsWith("DELETE ") && calls[0].endsWith("/devices/fid-1")).isEqualTo(
            true
        )
        assertThat(calls[1].startsWith("POST ") && calls[1].endsWith("/authentication/logout")).isEqualTo(
            true
        )
        assertThat(logoutBody.contains("\"device_token\":\"fid-1\"")).isTrue()
        assertThat(logoutBody.contains("\"refresh_token\":\"refresh-1\"")).isTrue()
    }

    @Test
    fun `logout keeps the installation id but clears local state even when both calls fail`() =
        runTest {
            val storage = InMemoryTokenStorage().apply { saveTokens("access-1", "refresh-1") }
            val installationIds =
                PushInstallationIdHolder(InMemorySecurePreferences()).apply { onRegistered("fid-1") }
            val registrationStore = DeviceRegistrationStore(InMemorySecurePreferences()).apply {
                record("fid-1", "42", Instant.fromEpochMilliseconds(0))
            }
            val client =
                jsonClient { respond(content = "", status = HttpStatusCode.InternalServerError) }

            val result = authSource(client, storage, installationIds, registrationStore).logout()

            assertThat(result).isEqualTo(Result.Success(Unit))
            assertThat(storage.getAccessToken()).isNull()
            assertThat(storage.getRefreshToken()).isNull()
            assertThat(registrationStore.lastRegisteredAt("fid-1", "42")).isNull()
            assertThat(installationIds.lastKnown()).isEqualTo("fid-1")
        }

    @Test
    fun `logout without an installation id skips the unregister call`() = runTest {
        val methods = mutableListOf<String>()
        val storage = InMemoryTokenStorage().apply { saveTokens("access-1", "refresh-1") }
        val client = jsonClient { request ->
            methods += request.method.value
            respond(content = "", status = HttpStatusCode.OK)
        }

        authSource(client, storage).logout()

        assertThat(methods).isEqualTo(listOf("POST"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a cancelled logout still clears local state`() = runTest {
        val storage = InMemoryTokenStorage().apply { saveTokens("access-1", "refresh-1") }
        val installationIds =
            PushInstallationIdHolder(InMemorySecurePreferences()).apply { onRegistered("fid-1") }
        val registrationStore = DeviceRegistrationStore(InMemorySecurePreferences()).apply {
            record("fid-1", "42", Instant.fromEpochMilliseconds(0))
        }
        val client = jsonClient { awaitCancellation() }

        val job =
            launch { authSource(client, storage, installationIds, registrationStore).logout() }
        runCurrent()
        job.cancelAndJoin()

        assertThat(storage.getAccessToken()).isNull()
        assertThat(registrationStore.lastRegisteredAt("fid-1", "42")).isNull()
    }
}
