package com.martdev.flickq.feature.payment.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.payment.domain.PaymentRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val paymentDataModule = module {
    if (AppConfig.USE_FAKES) {
        singleOf(::FakePaymentDataSource) { bind<PaymentRepository>() }
    } else {
        single<PaymentRepository> { RealPaymentDataSource(get()) }
    }
}
