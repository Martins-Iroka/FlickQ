package com.martdev.flickq.feature.movie.data

import com.martdev.flickq.feature.movie.domain.MovieRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val movieDataModule = module {
    singleOf(::FakeMovieDataSource) { bind<MovieRepository>() }
}
