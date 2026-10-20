package com.martdev.flickq.push

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.martdev.flickq.core.data.push.PushInstallationIdHolder
import com.martdev.flickq.feature.notification.domain.PushSessionProvider
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/**
 * FCM entry point. Registration uses the Firebase Installation ID ([onRegistered]); the
 * deprecated `onNewToken` is deliberately not overridden.
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class FlickQMessagingService : FirebaseMessagingService() {

    private val installationIds: PushInstallationIdHolder by inject()
    private val sessionProvider: PushSessionProvider by inject()

    /** First registration, rotation (reinstall, clear data, restore) and routine re-sync. */
    override fun onRegistered(installationId: String) {
        installationIds.onRegistered(installationId)
        RegisterDeviceWorker.enqueue(applicationContext)
    }

    /** Only foreground notification messages reach here; background ones go to the tray. */
    override fun onMessageReceived(message: RemoteMessage) {
        // Signed out → this may be a previous user's push still routed here; don't show it.
        // FCM calls this on a background thread, and the session read is a local token lookup.
        val signedIn = runBlocking { sessionProvider.current() != null }
        if (!signedIn) return
        PushNotificationPresenter(applicationContext).show(message)
    }
}