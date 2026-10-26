package com.martdev.flickq.feature.notification.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberNotificationPermission(
    onResult: (NotificationPermissionStatus) -> Unit,
): NotificationPermission {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember { IosNotificationPermission { currentOnResult(it) } }
}

/**
 * UNUserNotificationCenter reports its settings only asynchronously, so [status] returns the
 * last value read and starts a refresh; every refresh reports through [onStatus] on the main
 * thread. Until the first read lands, the status is UNSUPPORTED, which keeps the card hidden.
 */
private class IosNotificationPermission(
    private val onStatus: (NotificationPermissionStatus) -> Unit,
) : NotificationPermission {

    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private var lastStatus = NotificationPermissionStatus.UNSUPPORTED

    override fun status(): NotificationPermissionStatus {
        refresh()
        return lastStatus
    }

    override fun request() {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> refresh() }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    private fun refresh() {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus.toPermissionStatus()
            dispatch_async(dispatch_get_main_queue()) {
                lastStatus = status
                onStatus(status)
            }
        }
    }
}

private fun UNAuthorizationStatus?.toPermissionStatus(): NotificationPermissionStatus = when (this) {
    UNAuthorizationStatusAuthorized,
    UNAuthorizationStatusProvisional,
    UNAuthorizationStatusEphemeral -> NotificationPermissionStatus.GRANTED
    // iOS shows the system dialog only once; after that, only Settings can change it.
    UNAuthorizationStatusNotDetermined -> NotificationPermissionStatus.REQUESTABLE
    UNAuthorizationStatusDenied -> NotificationPermissionStatus.BLOCKED
    else -> NotificationPermissionStatus.UNDETERMINED
}