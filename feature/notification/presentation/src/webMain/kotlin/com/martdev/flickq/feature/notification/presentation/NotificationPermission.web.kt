package com.martdev.flickq.feature.notification.presentation

import androidx.compose.runtime.Composable

/** No push on this platform; the prompt never shows. */
@Composable
actual fun rememberNotificationPermission(
    onResult: (NotificationPermissionStatus) -> Unit,
): NotificationPermission = UnsupportedNotificationPermission