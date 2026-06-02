package com.martdev.flickq

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.martdev.flickq.core.data.SessionManager
import com.martdev.flickq.core.designsystem.FlickQTheme
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.auth.presentation.AuthGraphRoute
import com.martdev.flickq.feature.auth.presentation.LogoutViewModel
import com.martdev.flickq.feature.auth.presentation.authGraph
import org.koin.compose.koinInject
import com.martdev.flickq.feature.booking.presentation.SeatSelectionRoute
import com.martdev.flickq.feature.booking.presentation.bookingGraph
import com.martdev.flickq.feature.movie.presentation.MovieGraphRoute
import com.martdev.flickq.feature.movie.presentation.movieGraph
import com.martdev.flickq.feature.payment.presentation.PaymentRoute
import com.martdev.flickq.feature.payment.presentation.paymentGraph
import com.martdev.flickq.feature.showtime.presentation.ShowtimeListRoute
import com.martdev.flickq.feature.showtime.presentation.showtimeGraph
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FlickQApp() {
    FlickQTheme {
        val navController = rememberNavController()
        val sessionManager = koinInject<SessionManager>()
        val logoutViewModel = koinViewModel<LogoutViewModel>()
        // Refresh token expired/revoked anywhere in the app → clear the stack and re-auth.
        ObserveAsEvents(sessionManager.events) {
            navController.navigate(AuthGraphRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
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
                onViewShowtimes = { movieId -> navController.navigate(ShowtimeListRoute(movieId)) },
                // Logout revokes the session; the SessionManager observer above routes to login.
                onLogout = { logoutViewModel.logout() },
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
                },
                // Hold lapsed (seats released) → the reservation and its on-stack seat selection
                // are both stale, so start over at browse rather than land on a stale seat map.
                onReservationExpired = {
                    navController.navigate(MovieGraphRoute) {
                        popUpTo(MovieGraphRoute) { inclusive = true }
                    }
                },
            )
        }
    }
}
