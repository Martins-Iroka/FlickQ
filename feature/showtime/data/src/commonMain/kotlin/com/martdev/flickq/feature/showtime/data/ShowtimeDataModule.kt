package com.martdev.flickq.feature.showtime.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.showtime.domain.ShowtimeRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val showtimeDataModule = module {
    if (AppConfig.USE_FAKES) {
        singleOf(::FakeShowtimeDataSource) { bind<ShowtimeRepository>() }
    } else {
        single<ShowtimeRepository> { RealShowtimeDataSource(get()) }
    }
}
