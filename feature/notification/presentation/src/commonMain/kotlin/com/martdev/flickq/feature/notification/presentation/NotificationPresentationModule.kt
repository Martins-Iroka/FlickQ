package com.martdev.flickq.feature.notification.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val notificationPresentationModule = module {
    single { PushDeepLinkDispatcher() }
    viewModel { NotificationPromptViewModel(get()) }
}