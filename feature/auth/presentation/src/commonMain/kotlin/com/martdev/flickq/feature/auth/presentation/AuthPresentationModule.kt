package com.martdev.flickq.feature.auth.presentation

import com.martdev.flickq.feature.auth.presentation.login.LoginViewModel
import com.martdev.flickq.feature.auth.presentation.otp.OtpVerifyViewModel
import com.martdev.flickq.feature.auth.presentation.register.RegisterViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::LogoutViewModel)
    viewModel { params -> OtpVerifyViewModel(params.get(), params.get(), get()) }
}
