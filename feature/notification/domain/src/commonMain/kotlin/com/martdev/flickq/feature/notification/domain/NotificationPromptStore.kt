package com.martdev.flickq.feature.notification.domain

import kotlin.time.Instant

/** What the soft notification prompt remembers between sessions. */
interface NotificationPromptStore {
    fun snoozedUntil(): Instant?
    fun snoozeUntil(until: Instant)

    /** Whether the system permission dialog was ever requested (tells "never asked" from "blocked"). */
    fun hasRequestedPermission(): Boolean
    fun markPermissionRequested()
}