package com.martdev.flickq.feature.payment.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class PaymentRoute(val reservationId: Long)

/**
 * The payment feature's nav graph: initialize + verify a payment for a
 * reservation and confirm. [onDone] is the cross-feature exit (back to browse,
 * wired in :app:shared); back during the flow returns to the ticket.
 * [onReservationExpired] (also wired in :app:shared) pops to seat selection so the
 * user can re-pick when the hold lapsed.
 */
fun NavGraphBuilder.paymentGraph(
    navController: NavController,
    onDone: () -> Unit,
    onReservationExpired: () -> Unit,
) {
    composable<PaymentRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<PaymentRoute>()
        PaymentRoot(
            reservationId = route.reservationId,
            onDone = onDone,
            onNavigateBack = { navController.popBackStack() },
            onReservationExpired = onReservationExpired,
        )
    }
}
