package com.martdev.flickq.feature.movie.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.movie.domain.MovieRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val movieDataModule = module {
    if (AppConfig.USE_FAKES) {
        singleOf(::FakeMovieDataSource) { bind<MovieRepository>() }
    } else {
        single<MovieRepository> { RealMovieDataSource(get()) }
    }
}
