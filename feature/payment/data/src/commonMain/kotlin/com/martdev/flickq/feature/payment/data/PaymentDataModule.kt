package com.martdev.flickq.feature.payment.data

import com.martdev.flickq.feature.payment.domain.PaymentRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val paymentDataModule = module {
    singleOf(::FakePaymentDataSource) { bind<PaymentRepository>() }
}
