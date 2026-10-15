package com.martdev.flickq.core.data.push

import io.ktor.util.sha1
import kotlin.time.Instant

/**
 * Remembers the last successful `PUT /devices` as `{fidHash, userId, registeredAt}` so the client
 * re-registers at most once a day per (FID, user). Only a hash of the FID is kept here.
 *
 * Cleared on logout and on session expiry, so the next login always re-PUTs and moves the FID to
 * whoever signs in.
 */
class DeviceRegistrationStore(
    private val preferences: SecurePreferences,
) {

    /** When [fid] was last registered for [userId], or `null` if never (or for someone else). */
    fun lastRegisteredAt(fid: String, userId: String): Instant? {
        val hash = preferences.getString(KEY_FID_HASH) ?: return null
        val owner = preferences.getString(KEY_USER_ID) ?: return null
        val at = preferences.getString(KEY_REGISTERED_AT)?.toLongOrNull() ?: return null
        if (hash != hashOf(fid) || owner != userId) return null
        return Instant.fromEpochMilliseconds(at)
    }

    fun record(fid: String, userId: String, at: Instant) {
        preferences.putString(KEY_FID_HASH, hashOf(fid))
        preferences.putString(KEY_USER_ID, userId)
        preferences.putString(KEY_REGISTERED_AT, at.toEpochMilliseconds().toString())
    }

    fun clear() {
        preferences.remove(KEY_FID_HASH)
        preferences.remove(KEY_USER_ID)
        preferences.remove(KEY_REGISTERED_AT)
    }

    private fun hashOf(fid: String): String = sha1(fid.encodeToByteArray()).toHexString()

    private companion object {
        const val KEY_FID_HASH = "flickq.push.registered_fid_hash"
        const val KEY_USER_ID = "flickq.push.registered_user_id"
        const val KEY_REGISTERED_AT = "flickq.push.registered_at"
    }
}