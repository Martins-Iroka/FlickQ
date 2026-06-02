package com.martdev.flickq.core.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionExpiryTest {

    @Test
    fun `refresh failure clears tokens and signals session expiry`() = runTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveTokens(accessToken = "expired-access", refreshToken = "expired-refresh")
        }
        val sessionManager = SessionManager()
        val received = mutableListOf<SessionEvent>()
        val collector = launch { sessionManager.events.collect { received += it } }
        runCurrent() // let the collector subscribe before the request fires

        // Every call (protected request + the refresh POST) comes back 401, so the Bearer
        // plugin tries to refresh, that also 401s, and the refresh path gives up.
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.Unauthorized) }
        val client = HttpClientFactory.create(engine, tokenStorage, sessionManager)

        runCatching { client.get("${AppConfig.BASE_URL}/movie/get-movies") }
        advanceUntilIdle()
        collector.cancel()

        assertThat(tokenStorage.getAccessToken()).isNull()
        assertThat(tokenStorage.getRefreshToken()).isNull()
        assertThat(received).isEqualTo(listOf(SessionEvent.Expired))
    }

    @Test
    fun `notifyExpired emits a single Expired event`() = runTest {
        val sessionManager = SessionManager()
        val received = mutableListOf<SessionEvent>()
        val collector = launch { sessionManager.events.collect { received += it } }
        runCurrent()

        sessionManager.notifyExpired()
        advanceUntilIdle()
        collector.cancel()

        assertThat(received).isEqualTo(listOf(SessionEvent.Expired))
    }
}
