package com.martdev.flickq.feature.reservation.presentation

import com.martdev.flickq.feature.reservation.presentation.myreservation.MyReservationViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val reservationPresentationModule = module {
    viewModelOf(::MyReservationViewModel)
}