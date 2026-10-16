package com.martdev.flickq.feature.notification.presentation

import com.martdev.flickq.feature.notification.domain.PushDeepLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A tapped notification's deep link, waiting for the app to navigate to it. */
data class PendingPushDeepLink(val link: PushDeepLink, val submittedAt: Instant)

/**
 * Holds the deep link from the last notification tap until the app can navigate to it. Taps from
 * every entry point (cold start, background, foreground) land here, so navigation has one path.
 *
 * A link that waits longer than [TTL] (e.g. the user was signed out and took a while to sign in)
 * is dropped instead of yanking them somewhere stale. Call [clear] on logout and session expiry.
 */
class PushDeepLinkDispatcher(
    private val clock: Clock = Clock.System,
) {

    /** The waiting link, or `null`. Collect it, then [consume] before navigating. */
    val pending: StateFlow<PendingPushDeepLink?>
        field = MutableStateFlow<PendingPushDeepLink?>(null)

    fun submit(link: PushDeepLink) {
        pending.value = PendingPushDeepLink(link, clock.now())
    }

    /** Takes the waiting link, or `null` if there is none, or it has expired. Clears it either way. */
    fun consume(): PushDeepLink? {
        val entry = pending.getAndUpdate { null } ?: return null
        return entry.link.takeIf { clock.now() - entry.submittedAt <= TTL }
    }

    fun clear() {
        pending.value = null
    }

    companion object {
        val TTL: Duration = 10.minutes
    }
}