package com.martdev.flickq.feature.notification.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

enum class NotificationPermissionStatus {
    GRANTED,

    /** The system dialog can be shown. */
    REQUESTABLE,

    /** Not granted, and the platform can't tell "never asked" from "permanently denied". */
    UNDETERMINED,

    /** Only the system settings can turn notifications on. */
    BLOCKED,

    /** No push on this platform (web, JVM). */
    UNSUPPORTED,
}

@Stable
interface NotificationPermission {
    fun status(): NotificationPermissionStatus
    fun request()
    fun openSettings()
}

/** [onResult] receives the fresh status once the system dialog closes. */
@Composable
expect fun rememberNotificationPermission(
    onResult: (NotificationPermissionStatus) -> Unit,
): NotificationPermission

internal object UnsupportedNotificationPermission : NotificationPermission {
    override fun status() = NotificationPermissionStatus.UNSUPPORTED
    override fun request() = Unit
    override fun openSettings() = Unit
}