package com.martdev.flickq.feature.reservation.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.martdev.flickq.feature.reservation.presentation.myreservation.MyReservationScreen
import kotlinx.serialization.Serializable

@Serializable
data object ReservationGraphRoute

@Serializable
data object ReservationListRoute

fun NavGraphBuilder.reservationGraph(
    navController: NavController,
    onPay: (Long) -> Unit
) {
    navigation<ReservationGraphRoute>(startDestination = ReservationListRoute) {
        composable<ReservationListRoute> {
            MyReservationScreen {
                onPay(it)
            }
        }
    }
}
