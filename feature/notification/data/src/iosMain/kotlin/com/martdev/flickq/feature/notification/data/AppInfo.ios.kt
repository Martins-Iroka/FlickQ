package com.martdev.flickq.feature.notification.data

import com.martdev.flickq.notification.model.DevicePlatform
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

actual fun notificationDataPlatformModule(): Module = module {
    single {
        AppInfo(
            platform = DevicePlatform.IOS,
            appVersion = AppInfoSanitizer.appVersion(
                NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ),
            // e.g. "en_NG@calendar=Gregorian"; the sanitizer strips the keyword suffix.
            locale = AppInfoSanitizer.locale(NSLocale.currentLocale.localeIdentifier),
        )
    }
}