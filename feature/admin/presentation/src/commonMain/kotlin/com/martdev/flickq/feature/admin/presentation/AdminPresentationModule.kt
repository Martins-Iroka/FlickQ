package com.martdev.flickq.feature.admin.presentation

import com.martdev.flickq.feature.admin.presentation.login.AdminLoginViewModel
import com.martdev.flickq.feature.admin.presentation.reports.AdminReportsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val adminPresentationModule = module {
    viewModelOf(::AdminLoginViewModel)
    viewModelOf(::AdminReportsViewModel)
}
