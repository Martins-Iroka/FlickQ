package com.martdev.flickq.core.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide signal that the session ended unexpectedly — the refresh token was rejected
 * (expired/revoked), so the user must re-authenticate. The shared `HttpClient`'s refresh
 * path calls [notifyExpired] after clearing tokens; the root composables observe [events]
 * and route back to login, clearing the back stack.
 *
 * [notifyExpired] is non-suspending (`tryEmit`) so it can fire from Ktor's `refreshTokens`
 * lambda without a coroutine scope; `extraBufferCapacity = 1` lets the emit land even when
 * no collector is momentarily active (e.g. mid-recomposition). `replay = 0` so a freshly
 * mounted root doesn't replay a stale expiry and bounce the user out again.
 */
class SessionManager {
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    fun notifyExpired() {
        _events.tryEmit(SessionEvent.Expired)
    }
}

enum class SessionEvent { Expired }
