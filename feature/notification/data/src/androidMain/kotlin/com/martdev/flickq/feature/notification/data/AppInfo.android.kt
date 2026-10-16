package com.martdev.flickq.feature.notification.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.martdev.flickq.notification.model.DevicePlatform
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Locale

actual fun notificationDataPlatformModule(): Module = module {
    single {
        val context = androidContext()
        AppInfo(
            platform = DevicePlatform.ANDROID,
            appVersion = AppInfoSanitizer.appVersion(context.versionName()),
            locale = AppInfoSanitizer.locale(Locale.getDefault().toLanguageTag()),
        )
    }
}

private fun Context.versionName(): String? = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    info.versionName
} catch (_: PackageManager.NameNotFoundException) {
    null
}