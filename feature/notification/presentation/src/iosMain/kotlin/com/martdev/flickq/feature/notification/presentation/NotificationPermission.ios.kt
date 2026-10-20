package com.martdev.flickq.feature.notification.presentation

import androidx.compose.runtime.Composable

// Phase 3 replaces this with UNUserNotificationCenter.
@Composable
actual fun rememberNotificationPermission(
    onResult: (NotificationPermissionStatus) -> Unit,
): NotificationPermission = UnsupportedNotificationPermission