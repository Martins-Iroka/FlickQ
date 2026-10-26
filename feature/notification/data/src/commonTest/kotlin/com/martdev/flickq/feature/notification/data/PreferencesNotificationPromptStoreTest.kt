package com.martdev.flickq.feature.notification.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.core.data.push.InMemorySecurePreferences
import kotlin.test.Test
import kotlin.time.Instant

class PreferencesNotificationPromptStoreTest {

    private val store = PreferencesNotificationPromptStore(InMemorySecurePreferences())

    @Test
    fun `starts with no snooze and no request`() {
        assertThat(store.snoozedUntil()).isNull()
        assertThat(store.hasRequestedPermission()).isFalse()
    }

    @Test
    fun `round-trips the snooze and the request flag`() {
        val until = Instant.fromEpochMilliseconds(1_700_000_000_000)
        store.snoozeUntil(until)
        store.markPermissionRequested()
        assertThat(store.snoozedUntil()).isEqualTo(until)
        assertThat(store.hasRequestedPermission()).isTrue()
    }
}