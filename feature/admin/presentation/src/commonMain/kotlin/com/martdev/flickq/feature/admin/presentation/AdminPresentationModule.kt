package com.martdev.flickq.feature.admin.presentation

import com.martdev.flickq.feature.admin.presentation.genres.AdminGenresViewModel
import com.martdev.flickq.feature.admin.presentation.logic.adminPresentationLogicModule
import com.martdev.flickq.feature.admin.presentation.reservations.AdminReservationDetailViewModel
import com.martdev.flickq.feature.admin.presentation.reservations.AdminReservationsViewModel
import com.martdev.flickq.feature.admin.presentation.showtimes.AdminShowtimesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val adminPresentationModule = module {
    // Login + hub + reports + movies + rooms ViewModels now live in :feature:admin:presentation-logic
    // (shared with the Kobweb admin app).
    includes(adminPresentationLogicModule)
    viewModelOf(::AdminGenresViewModel)
    viewModelOf(::AdminShowtimesViewModel)
    viewModelOf(::AdminReservationsViewModel)
    viewModel { (reservationId: Long) -> AdminReservationDetailViewModel(reservationId, get(), get()) }
}
