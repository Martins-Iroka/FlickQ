package com.martdev.flickq

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.martdev.flickq.core.data.SessionManager
import com.martdev.flickq.core.designsystem.FlickQTheme
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.auth.presentation.AuthGraphRoute
import com.martdev.flickq.feature.auth.presentation.LogoutViewModel
import com.martdev.flickq.feature.auth.presentation.authGraph
import com.martdev.flickq.feature.booking.presentation.SeatSelectionRoute
import com.martdev.flickq.feature.booking.presentation.bookingGraph
import com.martdev.flickq.feature.movie.presentation.MovieGraphRoute
import com.martdev.flickq.feature.movie.presentation.MovieListRoute
import com.martdev.flickq.feature.movie.presentation.movieGraph
import com.martdev.flickq.feature.payment.presentation.PaymentRoute
import com.martdev.flickq.feature.payment.presentation.paymentGraph
import com.martdev.flickq.feature.reservation.presentation.ReservationGraphRoute
import com.martdev.flickq.feature.reservation.presentation.ReservationListRoute
import com.martdev.flickq.feature.reservation.presentation.reservationGraph
import com.martdev.flickq.feature.showtime.presentation.ShowtimeListRoute
import com.martdev.flickq.feature.showtime.presentation.showtimeGraph
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FlickQApp() {
    FlickQTheme {
        val navController = rememberNavController()
        val sessionManager = koinInject<SessionManager>()
        var hasAuthenticated by remember {
            mutableStateOf(false)
        }

        val onLogout = if (hasAuthenticated) {
            val logoutViewModel = koinViewModel<LogoutViewModel>()
            logoutViewModel::logout
        } else {
            {}
        }
        val selectedIcons = listOf(
            Icons.Filled.Movie,
            Icons.Filled.Receipt
        )
        val unselectedIcons = listOf(
            Icons.Outlined.Movie,
            Icons.Outlined.Receipt
        )

        var selectedItem by remember {
            mutableIntStateOf(0)
        }

        var showNavBar by remember {
            mutableStateOf(false)
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            destination.route?.let {
                showNavBar = it.contains(MovieListRoute.toString()) || it.contains(ReservationListRoute.toString())
            }
        }
        // Refresh token expired/revoked anywhere in the app → clear the stack and re-auth.
        ObserveAsEvents(sessionManager.events) {
            navController.navigate(AuthGraphRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        Scaffold(
            bottomBar = {
                if (showNavBar) {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        NavigationBarItem(
                            selectedItem == 0,
                            onClick = {
                                selectedItem = 0
                                navController.navigate(MovieGraphRoute)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selectedItem == 0) selectedIcons.first() else unselectedIcons.first(),
                                    contentDescription = "movie"
                                )
                            },
                            label = {
                                Text("Movies")
                            }
                        )

                        NavigationBarItem(
                            selectedItem == 1,
                            onClick = {
                                selectedItem = 1
                                navController.navigate(ReservationGraphRoute)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selectedItem == 1) selectedIcons.last() else unselectedIcons.last(),
                                    contentDescription = "ticket"
                                )
                            },
                            label = {
                                Text("Reservation")
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AuthGraphRoute,
                modifier = Modifier.padding(innerPadding)
            ) {
                authGraph(
                    navController = navController,
                    onAuthenticated = {
                        hasAuthenticated = true
                        navController.navigate(MovieGraphRoute) {
                            popUpTo(AuthGraphRoute) { inclusive = true }
                        }
                    }
                )
                movieGraph(
                    navController = navController,
                    onViewShowtimes = { movieId -> navController.navigate(ShowtimeListRoute(movieId)) },
                    // Logout revokes the session; the SessionManager observer above routes to login.
                    onLogout = onLogout,
                )
                showtimeGraph(
                    navController = navController,
                    onPickShowtime = { showtimeId ->
                        navController.navigate(
                            SeatSelectionRoute(
                                showtimeId
                            )
                        )
                    }
                )
                bookingGraph(
                    navController = navController,
                    onProceedToPayment = { reservationId ->
                        navController.navigate(
                            PaymentRoute(
                                reservationId
                            )
                        )
                    },
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
                reservationGraph(
                    navController = navController
                ) { reservationId ->
                    navController.navigate(
                        PaymentRoute(
                            reservationId, true
                        )
                    )
                }
            }
        }
    }
}
