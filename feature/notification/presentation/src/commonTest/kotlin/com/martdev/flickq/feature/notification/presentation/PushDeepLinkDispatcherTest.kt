package com.martdev.flickq.feature.notification.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.martdev.flickq.feature.notification.domain.PushDeepLink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class MutableClock(var now: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)) : Clock {
    override fun now(): Instant = now
    fun advance(by: Duration) {
        now += by
    }
}

class PushDeepLinkDispatcherTest {

    private val clock = MutableClock()
    private val dispatcher = PushDeepLinkDispatcher(clock)

    @Test
    fun `a submitted link is exposed until consumed`() = runTest {
        dispatcher.pending.test {
            assertThat(awaitItem()).isNull()

            dispatcher.submit(PushDeepLink.Ticket(5))
            assertThat(awaitItem()?.link).isEqualTo(PushDeepLink.Ticket(5))

            assertThat(dispatcher.consume()).isEqualTo(PushDeepLink.Ticket(5))
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `consume is one-shot`() {
        dispatcher.submit(PushDeepLink.Movie(3))

        assertThat(dispatcher.consume()).isEqualTo(PushDeepLink.Movie(3))
        assertThat(dispatcher.consume()).isNull()
    }

    @Test
    fun `a newer tap replaces a waiting one`() {
        dispatcher.submit(PushDeepLink.Movie(3))
        dispatcher.submit(PushDeepLink.Ticket(9))

        assertThat(dispatcher.consume()).isEqualTo(PushDeepLink.Ticket(9))
    }

    @Test
    fun `a link older than ten minutes is dropped`() {
        dispatcher.submit(PushDeepLink.Ticket(5))
        clock.advance(10.minutes + 1.seconds)

        assertThat(dispatcher.consume()).isNull()
        assertThat(dispatcher.pending.value).isNull()
    }

    @Test
    fun `a link within ten minutes survives`() {
        dispatcher.submit(PushDeepLink.Ticket(5))
        clock.advance(10.minutes)

        assertThat(dispatcher.consume()).isEqualTo(PushDeepLink.Ticket(5))
    }

    @Test
    fun `clear drops the waiting link`() {
        dispatcher.submit(PushDeepLink.Ticket(5))

        dispatcher.clear()

        assertThat(dispatcher.pending.value).isNull()
        assertThat(dispatcher.consume()).isNull()
    }
}