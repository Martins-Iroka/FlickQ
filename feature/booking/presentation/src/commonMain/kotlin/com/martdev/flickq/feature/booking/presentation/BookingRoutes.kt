package com.martdev.flickq.feature.booking.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.martdev.flickq.feature.booking.presentation.seat.SeatSelectionRoot
import com.martdev.flickq.feature.booking.presentation.ticket.TicketRoot
import kotlinx.serialization.Serializable

@Serializable
data class SeatSelectionRoute(val showtimeId: Long)

@Serializable
data class TicketRoute(val reservationId: Long)

/**
 * The booking feature's nav graph: pick seats, create a reservation, view the
 * ticket. Seat selection -> ticket is intra-feature; [onProceedToPayment] and
 * [onExitToBrowse] are the cross-feature exits, wired in :app:shared.
 */
fun NavGraphBuilder.bookingGraph(
    navController: NavController,
    onProceedToPayment: (Long) -> Unit,
    onExitToBrowse: () -> Unit
) {
    composable<SeatSelectionRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SeatSelectionRoute>()
        SeatSelectionRoot(
            showtimeId = route.showtimeId,
            onReserved = { reservationId ->
                navController.navigate(TicketRoute(reservationId)) {
                    popUpTo<SeatSelectionRoute> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable<TicketRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<TicketRoute>()
        TicketRoot(
            reservationId = route.reservationId,
            onProceedToPayment = onProceedToPayment,
            onNavigateBack = onExitToBrowse
        )
    }
}
