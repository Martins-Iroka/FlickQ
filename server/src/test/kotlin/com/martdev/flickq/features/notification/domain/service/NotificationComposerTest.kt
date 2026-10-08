package com.martdev.flickq.features.notification.domain.service

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class NotificationComposerTest {
    private val config = FcmConfig(
        false, "p", "", false, "dev-"
    )
    private val composer = NotificationComposer(config)

    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `movie added goes to the prefixed topic with a collapse key and deep link`() {
        val movieAdded = NotificationEvent.MovieAdded(
            7, "Dune", "https://img/p.jpg"
        )
        val d = composer.compose(movieAdded)

        assertThat(d.type).isEqualTo(NotificationType.MOVIE_ADDED)
        assertThat(d.targetKind).isEqualTo(NotificationTargetKind.TOPIC)
        assertThat(d.topic).isEqualTo("dev-new-movies")
        assertThat(d.idempotencyKey).isEqualTo("movie_added:7")

        assertThat(d.push.data["deeplink"]).isEqualTo("flickq://movie/7")
        assertThat(d.push.collapseKey).isEqualTo("new-movies")
        assertThat(d.push.highPriority).isFalse()
        assertThat(d.push.imageUrl).isEqualTo("https://img/p.jpg")
    }

    @Test
    fun `reservation held targets the user, expires with the hold and is high priority`() {
        val expires = now + 15.minutes
        val reservationHeld = NotificationEvent.ReservationHeld(
            9, 42, "Dune", listOf("A1", "A2"), expires
        )
        val d = composer.compose(reservationHeld)

        assertThat(d.targetKind).isEqualTo(NotificationTargetKind.USER)
        assertThat(d.userId).isEqualTo(42L)
        assertThat(d.expiresAt).isEqualTo(expires)
        assertThat(d.push.highPriority).isTrue()
        assertThat(d.push.body).contains("A1", "A2")
        assertThat(d.idempotencyKey).isEqualTo("reservation_held:9")
    }

    @Test
    fun `booking confirmed shares the collapse key with seats held so it replaces it`() {
        val reservationHeldEvent = NotificationEvent.ReservationHeld(
            9,
            42,
            "Dune",
            listOf("A1"),
            now)
        val bookingConfirmedEvent = NotificationEvent.BookingConfirmed(
            9, 42, "Dune", now
        )
        val held = composer.compose(reservationHeldEvent)
        val done = composer.compose(bookingConfirmedEvent)

        assertThat(done.push.collapseKey).isEqualTo(held.push.collapseKey)
        assertThat(done.push.data["deeplink"]).isEqualTo("flickq://ticket/9")
        assertThat(done.idempotencyKey).isEqualTo("booking_confirmed:9")
    }

    @Test
    fun `subscribe draft carries the token but never puts it in the idempotency key`() {
        val token = "x".repeat(150)
        val d = composer.subscribeToNewMovies(token, now)

        assertThat(d.targetKind).isEqualTo(NotificationTargetKind.TOKENS)
        assertThat(d.pendingTokens).isEqualTo(listOf(token))
        assertThat(d.topic).isEqualTo("dev-new-movies")
        assertThat(d.idempotencyKey).doesNotContain(token)
    }

    @Test
    fun `subscribe key is stable within a UTC day and changes the next day`() {
        val token = "t".repeat(30)
        val key1 = composer.subscribeToNewMovies(token, now).idempotencyKey
        val key2 = composer.subscribeToNewMovies(token, now + 1.hours).idempotencyKey
        val key3 = composer.subscribeToNewMovies(token, now + 25.hours).idempotencyKey

        assertThat(key2).isEqualTo(key1)
        assertThat(key3).isNotEqualTo(key1)
    }

    @Test
    fun `sub and unsub keys never collide for the same token and day`() {
        val token = "t".repeat(30)
        val subIdempotencyKey = composer.subscribeToNewMovies(token, now).idempotencyKey
        val unsubIdempotencyKey = composer.unsubscribeFromNewMovies(token, now).idempotencyKey

        assertThat(subIdempotencyKey).isNotEqualTo(unsubIdempotencyKey)
    }
}