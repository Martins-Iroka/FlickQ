package com.martdev.flickq.push

import com.martdev.flickq.core.data.push.PushInstallationIdHolder
import com.martdev.flickq.feature.notification.domain.PushDeepLink
import com.martdev.flickq.feature.notification.domain.PushDeepLinkParser
import com.martdev.flickq.feature.notification.domain.PushSessionProvider
import com.martdev.flickq.feature.notification.domain.RegisterDeviceUseCase
import com.martdev.flickq.feature.notification.presentation.PushDeepLinkDispatcher
import com.martdev.flickq.startKoinIfNeeded
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

/**
 * The Swift-facing push entry point (`PushBridge.shared`). Only `:app:shared` is visible from
 * Swift, so the AppDelegate's FirebaseMessaging and UNUserNotificationCenter callbacks come
 * through here and are forwarded to the shared holder, dispatcher and use case.
 *
 * Anything that arrives before Koin starts is buffered and replayed by [start]. All calls come
 * from UIKit/FirebaseMessaging delegate callbacks on the main thread.
 *
 * The FID is never logged.
 */
object PushBridge {

    private val scope = MainScope()
    private var pendingInstallationId: String? = null
    private var pendingTap: PushDeepLink? = null

    private val koin: Koin? get() = KoinPlatform.getKoinOrNull()

    private val payloadKeys = listOf(
        PushDeepLinkParser.KEY_DEEPLINK,
        PushDeepLinkParser.KEY_TYPE,
        PushDeepLinkParser.KEY_RESERVATION_ID,
        PushDeepLinkParser.KEY_MOVIE_ID,
    )

    /** Starts Koin (if needed) and replays anything buffered. Call from `didFinishLaunching`. */
    fun start() {
        startKoinIfNeeded()
        pendingInstallationId?.let {
            pendingInstallationId = null
            onRegistered(it)
        }
        pendingTap?.let {
            pendingTap = null
            submit(it)
        }
    }

    /**
     * `messaging(_:didReceiveRegistration:)`: fires on every startup and on rotation; the
     * use case's 24 h debounce absorbs the repeats. Signed out → the use case skips, and the
     * next login registers instead. A failure is retried on the next foreground.
     */
    fun onRegistered(installationId: String) {
        val koin = koin ?: run {
            pendingInstallationId = installationId
            return
        }
        val holder = koin.get<PushInstallationIdHolder>()
        val registerDevice = koin.get<RegisterDeviceUseCase>()
        holder.onRegistered(installationId)
        scope.launch { registerDevice(holder.lastKnown()) }
    }

    /**
     * A notification tap (cold start, background or foreground). `userInfo` is untrusted;
     * only the allowlisted keys are read and the shared parser decides.
     */
    fun onNotificationTapped(userInfo: Map<Any?, *>) {
        val data = payloadKeys.associateWith { userInfo[it] as? String }
        val link = PushDeepLinkParser.parse(data) ?: return
        if (koin == null) {
            pendingTap = link
            return
        }
        submit(link)
    }

    /**
     * `willPresent`: show the banner only while signed in. Signed out, this may be a previous
     * user's push still routed to this device.
     */
    fun shouldPresentInForeground(completion: (Boolean) -> Unit) {
        val koin = koin ?: return completion(false)
        val sessions = koin.get<PushSessionProvider>()
        scope.launch { completion(sessions.current() != null) }
    }

    private fun submit(link: PushDeepLink) {
        koin?.get<PushDeepLinkDispatcher>()?.submit(link)
    }
}