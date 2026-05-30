package com.martdev.flickq.feature.movie.presentation

import com.martdev.flickq.feature.movie.presentation.detail.MovieDetailViewModel
import com.martdev.flickq.feature.movie.presentation.list.MovieListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val moviePresentationModule = module {
    viewModelOf(::MovieListViewModel)
    viewModel { params -> MovieDetailViewModel(params.get(), get()) }
}
