package com.martdev.flickq.feature.booking.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.booking.domain.BookingRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val bookingDataModule = module {
    if (AppConfig.USE_FAKES) {
        singleOf(::FakeBookingDataSource) { bind<BookingRepository>() }
    } else {
        single<BookingRepository> { RealBookingDataSource(get()) }
    }
}
