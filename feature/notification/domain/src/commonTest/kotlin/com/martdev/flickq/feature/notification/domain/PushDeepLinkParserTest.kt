package com.martdev.flickq.feature.notification.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class PushDeepLinkParserTest {

    @Test
    fun `parses ticket and movie links`() {
        assertThat(PushDeepLinkParser.parseLink("flickq://ticket/42")).isEqualTo(PushDeepLink.Ticket(42))
        assertThat(PushDeepLinkParser.parseLink("flickq://movie/7")).isEqualTo(PushDeepLink.Movie(7))
        assertThat(PushDeepLinkParser.parseLink("flickq://movie/9223372036854775807"))
            .isEqualTo(PushDeepLink.Movie(Long.MAX_VALUE))
    }

    @Test
    fun `rejects anything outside the allowlist`() {
        listOf(
            "flickq://profile/42",
            "flickq://ticket/0",
            "flickq://ticket/-1",
            "flickq://ticket/007",
            "flickq://ticket/abc",
            "flickq://ticket/42/extra",
            "flickq://ticket/42/",
            "flickq://ticket/",
            "flickq://ticket/42?next=https://evil.example",
            "flickq://ticket/42#frag",
            "flickq://user@ticket/42",
            "flickq://movie/9223372036854775808",
            "FLICKQ://ticket/42",
            "https://flickq.app/ticket/42",
            "http://ticket/42",
            "javascript://ticket/42",
            "javascript:alert(1)",
            " flickq://ticket/42",
            "",
        ).forEach { link ->
            assertThat(PushDeepLinkParser.parseLink(link), name = link).isNull()
        }
    }

    @Test
    fun `parse prefers the deeplink key`() {
        val data = mapOf("deeplink" to "flickq://ticket/5", "type" to "movie_added", "movieId" to "9")

        assertThat(PushDeepLinkParser.parse(data)).isEqualTo(PushDeepLink.Ticket(5))
    }

    @Test
    fun `an invalid deeplink fails closed instead of falling back`() {
        val data = mapOf("deeplink" to "https://evil.example", "type" to "booking_confirmed", "reservationId" to "5")

        assertThat(PushDeepLinkParser.parse(data)).isNull()
    }

    @Test
    fun `falls back to type and id keys when there is no deeplink`() {
        assertThat(PushDeepLinkParser.parse(mapOf("type" to "booking_confirmed", "reservationId" to "5")))
            .isEqualTo(PushDeepLink.Ticket(5))
        assertThat(PushDeepLinkParser.parse(mapOf("type" to "reservation_held", "reservationId" to "6")))
            .isEqualTo(PushDeepLink.Ticket(6))
        assertThat(PushDeepLinkParser.parse(mapOf("type" to "movie_added", "movieId" to "8")))
            .isEqualTo(PushDeepLink.Movie(8))
    }

    @Test
    fun `fallback rejects unknown types mismatched keys and bad ids`() {
        listOf(
            mapOf("type" to "promo", "movieId" to "8"),
            mapOf("type" to "movie_added", "reservationId" to "8"),
            mapOf("type" to "booking_confirmed", "reservationId" to "0"),
            mapOf("type" to "booking_confirmed", "reservationId" to "-3"),
            mapOf("type" to "booking_confirmed", "reservationId" to "1e3"),
            mapOf("type" to "booking_confirmed"),
            emptyMap(),
        ).forEach { data ->
            assertThat(PushDeepLinkParser.parse(data), name = data.toString()).isNull()
        }
    }
}