package com.martdev.flickq.feature.showtime.data

import com.martdev.flickq.feature.showtime.domain.ShowtimeRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val showtimeDataModule = module {
    singleOf(::FakeShowtimeDataSource) { bind<ShowtimeRepository>() }
}
