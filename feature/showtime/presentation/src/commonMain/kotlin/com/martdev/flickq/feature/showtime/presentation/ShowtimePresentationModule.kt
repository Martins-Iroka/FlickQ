package com.martdev.flickq.feature.showtime.presentation

import com.martdev.flickq.feature.showtime.presentation.list.ShowtimeListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val showtimePresentationModule = module {
    viewModel { params -> ShowtimeListViewModel(params.get(), get()) }
}
