package com.martdev.flickq.feature.notification.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.martdev.flickq.core.data.InMemoryTokenStorage
import com.martdev.flickq.feature.notification.domain.PushSession
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test

private fun jwtWith(payload: String): String {
    val encoded =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray())
    return "header.$encoded.signature"
}

class TokenPushSessionProviderTest {

    private suspend fun sessionFor(accessToken: String?): PushSession? {
        val storage = InMemoryTokenStorage()
        if (accessToken != null) storage.saveTokens(accessToken, "refresh")
        return TokenPushSessionProvider(storage).current()
    }

    @Test
    fun `a USER token is a customer session`() = runTest {
        assertThat(sessionFor(jwtWith("""{"userId":"42","role":"USER"}""")))
            .isEqualTo(PushSession(userId = "42", isCustomer = true))
    }

    @Test
    fun `other roles are not customers`() = runTest {
        assertThat(sessionFor(jwtWith("""{"userId":"1","role":"ADMIN"}"""))?.isCustomer).isEqualTo(
            false
        )
        assertThat(sessionFor(jwtWith("""{"userId":"1","role":"user"}"""))?.isCustomer).isEqualTo(
            false
        )
        assertThat(sessionFor(jwtWith("""{"userId":"1"}"""))?.isCustomer).isEqualTo(false)
    }

    @Test
    fun `no token a malformed token or a missing user id is no session`() = runTest {
        assertThat(sessionFor(null)).isNull()
        assertThat(sessionFor("not-a-jwt")).isNull()
        assertThat(sessionFor(jwtWith("""{"role":"USER"}"""))).isNull()
    }
}