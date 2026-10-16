package com.martdev.flickq.feature.notification.presentation

import org.koin.dsl.module

val notificationPresentationModule = module {
    single { PushDeepLinkDispatcher() }
}