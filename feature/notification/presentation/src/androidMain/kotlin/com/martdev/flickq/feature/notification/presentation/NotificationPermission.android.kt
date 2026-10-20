package com.martdev.flickq.feature.notification.presentation

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

@Composable
actual fun rememberNotificationPermission(
    onResult: (NotificationPermissionStatus) -> Unit,
): NotificationPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        currentOnResult(notificationPermissionStatus(context, activity))
    }
    return remember(context, activity, launcher) {
        AndroidNotificationPermission(context, activity, launcher)
    }
}

private class AndroidNotificationPermission(
    private val context: Context,
    private val activity: Activity?,
    private val launcher: ManagedActivityResultLauncher<String, Boolean>,
) : NotificationPermission {

    override fun status() = notificationPermissionStatus(context, activity)

    override fun request() {
        // Below API 33 there is no runtime permission; settings is the only switch.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return openSettings()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        }
        val launchContext =
            activity ?: context.also { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            launchContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No settings screen on this device; the card stays as it is.
        }
    }
}

private fun notificationPermissionStatus(
    context: Context,
    activity: Activity?
): NotificationPermissionStatus {
    val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return if (enabled) NotificationPermissionStatus.GRANTED else NotificationPermissionStatus.BLOCKED
    }
    val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    if (granted) {
        // Granted, but switched off in settings (app or all channels).
        return if (enabled) NotificationPermissionStatus.GRANTED else NotificationPermissionStatus.BLOCKED
    }
    val rationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
    return if (rationale) NotificationPermissionStatus.REQUESTABLE else NotificationPermissionStatus.UNDETERMINED
}