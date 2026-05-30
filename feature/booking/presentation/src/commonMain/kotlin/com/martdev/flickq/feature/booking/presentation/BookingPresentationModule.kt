package com.martdev.flickq.feature.booking.presentation

import com.martdev.flickq.feature.booking.presentation.seat.SeatSelectionViewModel
import com.martdev.flickq.feature.booking.presentation.ticket.TicketViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookingPresentationModule = module {
    viewModel { params -> SeatSelectionViewModel(params.get(), get()) }
    viewModel { params -> TicketViewModel(params.get(), get()) }
}
