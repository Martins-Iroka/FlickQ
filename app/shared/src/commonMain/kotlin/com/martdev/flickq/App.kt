package com.martdev.flickq

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.martdev.flickq.core.designsystem.FlickQTheme
import com.martdev.flickq.feature.auth.presentation.AuthGraphRoute
import com.martdev.flickq.feature.auth.presentation.authGraph
import com.martdev.flickq.feature.booking.presentation.SeatSelectionRoute
import com.martdev.flickq.feature.booking.presentation.bookingGraph
import com.martdev.flickq.feature.movie.presentation.MovieGraphRoute
import com.martdev.flickq.feature.movie.presentation.movieGraph
import com.martdev.flickq.feature.payment.presentation.PaymentRoute
import com.martdev.flickq.feature.payment.presentation.paymentGraph
import com.martdev.flickq.feature.showtime.presentation.ShowtimeListRoute
import com.martdev.flickq.feature.showtime.presentation.showtimeGraph

@Composable
fun FlickQApp() {
    FlickQTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = AuthGraphRoute) {
            authGraph(
                navController = navController,
                onAuthenticated = {
                    navController.navigate(MovieGraphRoute) {
                        popUpTo(AuthGraphRoute) { inclusive = true }
                    }
                }
            )
            movieGraph(
                navController = navController,
                onViewShowtimes = { movieId -> navController.navigate(ShowtimeListRoute(movieId)) }
            )
            showtimeGraph(
                navController = navController,
                onPickShowtime = { showtimeId -> navController.navigate(SeatSelectionRoute(showtimeId)) }
            )
            bookingGraph(
                navController = navController,
                onProceedToPayment = { reservationId -> navController.navigate(PaymentRoute(reservationId)) },
                onExitToBrowse = {
                    navController.navigate(MovieGraphRoute) {
                        popUpTo(MovieGraphRoute) { inclusive = true }
                    }
                }
            )
            paymentGraph(
                navController = navController,
                onDone = {
                    navController.navigate(MovieGraphRoute) {
                        popUpTo(MovieGraphRoute) { inclusive = true }
                    }
                }
            )
        }
    }
}
