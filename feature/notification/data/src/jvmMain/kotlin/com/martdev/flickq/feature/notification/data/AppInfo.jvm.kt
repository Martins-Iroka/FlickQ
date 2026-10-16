package com.martdev.flickq.feature.notification.data

import org.koin.core.module.Module
import org.koin.dsl.module

/** The server has no platform for this target, so it never registers for push. */
actual fun notificationDataPlatformModule(): Module = module {
    single {
        AppInfo(
            platform = null,
            appVersion = AppInfoSanitizer.FALLBACK_APP_VERSION,
            locale = AppInfoSanitizer.FALLBACK_LOCALE,
        )
    }
}