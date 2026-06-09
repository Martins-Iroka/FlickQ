package com.martdev.flickq.feature.admin.presentation

import com.martdev.flickq.feature.admin.presentation.genres.AdminGenresViewModel
import com.martdev.flickq.feature.admin.presentation.logic.adminPresentationLogicModule
import com.martdev.flickq.feature.admin.presentation.movies.AdminMoviesViewModel
import com.martdev.flickq.feature.admin.presentation.reports.AdminReportsViewModel
import com.martdev.flickq.feature.admin.presentation.reservations.AdminReservationDetailViewModel
import com.martdev.flickq.feature.admin.presentation.reservations.AdminReservationsViewModel
import com.martdev.flickq.feature.admin.presentation.rooms.AdminRoomsViewModel
import com.martdev.flickq.feature.admin.presentation.showtimes.AdminShowtimesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val adminPresentationModule = module {
    // Login + hub ViewModels now live in :feature:admin:presentation-logic (shared with Kobweb).
    includes(adminPresentationLogicModule)
    viewModelOf(::AdminReportsViewModel)
    viewModelOf(::AdminGenresViewModel)
    viewModelOf(::AdminMoviesViewModel)
    viewModelOf(::AdminRoomsViewModel)
    viewModelOf(::AdminShowtimesViewModel)
    viewModelOf(::AdminReservationsViewModel)
    viewModel { (reservationId: Long) -> AdminReservationDetailViewModel(reservationId, get(), get()) }
}
