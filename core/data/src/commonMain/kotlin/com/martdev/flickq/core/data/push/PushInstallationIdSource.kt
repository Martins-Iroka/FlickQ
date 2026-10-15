package com.martdev.flickq.core.data.push

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The device's Firebase Installation ID (FID), which FCM now uses in place of the deprecated
 * registration token. Lives in `:core:data` so logout (in `:feature:auth`) can read it without
 * depending on the notification feature.
 *
 * The FID is a stable per-install identifier shared with Analytics/Crashlytics — never log it.
 */
interface PushInstallationIdSource {
    /** Every FID Firebase delivers (first registration, rotation, routine re-sync). */
    val updates: Flow<String>

    /** The last FID Firebase delivered, persisted across process death; `null` if none yet. */
    fun lastKnown(): String?
}

/**
 * The single [PushInstallationIdSource]. The platform push entry points (Android
 * `FirebaseMessagingService.onRegistered`, iOS `didReceiveRegistration`) call [onRegistered],
 * possibly while no UI is alive, so the value is persisted before it is emitted.
 */
class PushInstallationIdHolder(
    private val preferences: SecurePreferences,
) : PushInstallationIdSource {

    override val updates: Flow<String>
        field = MutableSharedFlow<String>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override fun lastKnown(): String? = preferences.getString(KEY_FID)?.takeIf { it.isNotBlank() }

    fun onRegistered(installationId: String) {
        val fid = installationId.trim()
        if (fid.isEmpty()) return
        preferences.putString(KEY_FID, fid)
        updates.tryEmit(fid)
    }

    private companion object {
        const val KEY_FID = "flickq.push.fid"
    }
}