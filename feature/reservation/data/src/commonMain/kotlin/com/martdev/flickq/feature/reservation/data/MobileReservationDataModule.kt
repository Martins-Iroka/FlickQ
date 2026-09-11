package com.martdev.flickq.feature.reservation.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.reservation.domain.MobileReservationRepository
import org.koin.dsl.module

val reservationDataModule = module {
    single<MobileReservationRepository> {
        if (AppConfig.USE_FAKES) FakeReservationDataSource() else MobileReservationRepoImpl(get())
    }
}