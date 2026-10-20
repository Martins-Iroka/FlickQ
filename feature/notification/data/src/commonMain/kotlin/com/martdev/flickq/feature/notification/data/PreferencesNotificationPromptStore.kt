package com.martdev.flickq.feature.notification.data

import com.martdev.flickq.core.data.push.SecurePreferences
import com.martdev.flickq.feature.notification.domain.NotificationPromptStore
import kotlin.time.Instant

/** Lives in the push preferences, which are excluded from backup alongside the FID. */
class PreferencesNotificationPromptStore(
    private val preferences: SecurePreferences,
) : NotificationPromptStore {

    override fun snoozedUntil(): Instant? =
        preferences.getString(KEY_SNOOZED_UNTIL)?.toLongOrNull()
            ?.let(Instant::fromEpochMilliseconds)

    override fun snoozeUntil(until: Instant) {
        preferences.putString(KEY_SNOOZED_UNTIL, until.toEpochMilliseconds().toString())
    }

    override fun hasRequestedPermission(): Boolean = preferences.getString(KEY_REQUESTED) == "true"

    override fun markPermissionRequested() {
        preferences.putString(KEY_REQUESTED, "true")
    }

    private companion object {
        const val KEY_SNOOZED_UNTIL = "flickq.push.prompt_snoozed_until"
        const val KEY_REQUESTED = "flickq.push.permission_requested"
    }
}