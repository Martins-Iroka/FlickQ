package com.martdev.flickq.feature.notification.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.notification.domain.DeviceRegistrationLedger
import com.martdev.flickq.feature.notification.domain.DeviceRegistrationRepository
import com.martdev.flickq.feature.notification.domain.NotificationPromptStore
import com.martdev.flickq.feature.notification.domain.PushSessionProvider
import com.martdev.flickq.feature.notification.domain.RegisterDeviceUseCase
import org.koin.dsl.module

/** Needs [notificationDataPlatformModule] for [AppInfo]. */
val notificationDataModule = module {
    single<DeviceRegistrationRepository> {
        val appInfo = get<AppInfo>()
        val platform = appInfo.platform
        if (AppConfig.USE_FAKES || platform == null) {
            FakeDeviceRegistrationDataSource()
        } else {
            RealDeviceRegistrationDataSource(get(), platform, appInfo)
        }
    }
    single<DeviceRegistrationLedger> { StoreBackedRegistrationLedger(get()) }
    single<PushSessionProvider> { TokenPushSessionProvider(get()) }
    single { RegisterDeviceUseCase(get(), get(), get()) }
    single<NotificationPromptStore> { PreferencesNotificationPromptStore(get()) }
}