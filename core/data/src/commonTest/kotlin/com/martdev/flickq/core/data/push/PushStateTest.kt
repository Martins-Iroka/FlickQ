package com.martdev.flickq.core.data.push

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.core.data.HttpClientFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

class PushInstallationIdHolderTest {

    @Test
    fun `onRegistered persists the id and emits it`() = runTest {
        val preferences = InMemorySecurePreferences()
        val holder = PushInstallationIdHolder(preferences)

        holder.onRegistered("fid-1")

        assertThat(holder.updates.first()).isEqualTo("fid-1")
        // A fresh holder over the same store (process death) still knows the id.
        assertThat(PushInstallationIdHolder(preferences).lastKnown()).isEqualTo("fid-1")
    }

    @Test
    fun `a rotated id replaces the old one`() = runTest {
        val holder = PushInstallationIdHolder(InMemorySecurePreferences())

        holder.onRegistered("fid-1")
        holder.onRegistered("fid-2")

        assertThat(holder.lastKnown()).isEqualTo("fid-2")
        assertThat(holder.updates.first()).isEqualTo("fid-2")
    }

    @Test
    fun `a blank id is ignored`() {
        val holder = PushInstallationIdHolder(InMemorySecurePreferences())

        holder.onRegistered("fid-1")
        holder.onRegistered("   ")

        assertThat(holder.lastKnown()).isEqualTo("fid-1")
    }

    @Test
    fun `lastKnown is null before Firebase delivers an id`() {
        assertThat(PushInstallationIdHolder(InMemorySecurePreferences()).lastKnown()).isNull()
    }
}

class DeviceRegistrationStoreTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `returns the registration time for the same id and user`() {
        val store = DeviceRegistrationStore(InMemorySecurePreferences())

        store.record("fid-1", "42", at)

        assertThat(store.lastRegisteredAt("fid-1", "42")).isEqualTo(at)
    }

    @Test
    fun `a different id or user does not match`() {
        val store = DeviceRegistrationStore(InMemorySecurePreferences())

        store.record("fid-1", "42", at)

        assertThat(store.lastRegisteredAt("fid-2", "42")).isNull()
        assertThat(store.lastRegisteredAt("fid-1", "43")).isNull()
    }

    @Test
    fun `only a hash of the id is persisted`() {
        val preferences = InMemorySecurePreferences()
        val store = DeviceRegistrationStore(preferences)

        store.record("fid-plaintext", "42", at)

        val stored = listOf(
            "flickq.push.registered_fid_hash",
            "flickq.push.registered_user_id",
            "flickq.push.registered_at",
        ).mapNotNull { preferences.getString(it) }
        assertThat(stored.size).isEqualTo(3)
        assertThat(stored.any { it.contains("fid-plaintext") }).isFalse()
    }

    @Test
    fun `clear forgets the registration`() {
        val store = DeviceRegistrationStore(InMemorySecurePreferences())
        store.record("fid-1", "42", at)

        store.clear()

        assertThat(store.lastRegisteredAt("fid-1", "42")).isNull()
    }
}

class PushRoutesTest {

    @Test
    fun `device path-encodes the id`() {
        assertThat(PushRoutes.device("abc/def ghi")).isEqualTo("/devices/abc%2Fdef%20ghi")
    }

    @Test
    fun `isDeviceRoute matches the register and unregister paths only`() {
        assertThat(PushRoutes.isDeviceRoute("/api/v1/devices")).isTrue()
        assertThat(PushRoutes.isDeviceRoute("/api/v1/devices/fid-1")).isTrue()
        assertThat(PushRoutes.isDeviceRoute("/api/v1/movie/get-movies")).isFalse()
        assertThat(PushRoutes.isDeviceRoute("/api/v1/admin/devices-report")).isFalse()
    }
}

class HttpLogRedactionTest {

    @Test
    fun `requests carrying the installation id are not logged`() {
        assertThat(HttpClientFactory.isLoggable("/api/v1/devices")).isFalse()
        assertThat(HttpClientFactory.isLoggable("/api/v1/devices/fid-1")).isFalse()
        assertThat(HttpClientFactory.isLoggable("/api/v1/authentication/logout")).isFalse()
        assertThat(HttpClientFactory.isLoggable("/api/v1/movie/get-movies")).isTrue()
    }
}