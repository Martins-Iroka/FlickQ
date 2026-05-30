package com.martdev.flickq.feature.payment.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val paymentPresentationModule = module {
    viewModel { params -> PaymentViewModel(params.get(), get()) }
}
