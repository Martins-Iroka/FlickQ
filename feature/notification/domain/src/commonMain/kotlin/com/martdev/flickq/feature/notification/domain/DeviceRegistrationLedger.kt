package com.martdev.flickq.feature.notification.domain

import kotlin.time.Instant

/** Local record of the last successful registration, used to debounce re-registration. */
interface DeviceRegistrationLedger {
    /** When [fid] was last registered for [userId], or `null` if never (or for another user). */
    fun lastRegisteredAt(fid: String, userId: String): Instant?

    fun record(fid: String, userId: String, at: Instant)
}