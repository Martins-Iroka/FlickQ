package com.martdev.flickq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.FlickQTheme
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.feature.auth.presentation.AuthGraphRoute
import com.martdev.flickq.feature.auth.presentation.authGraph
import com.martdev.flickq.feature.booking.presentation.SeatSelectionRoute
import com.martdev.flickq.feature.booking.presentation.bookingGraph
import com.martdev.flickq.feature.movie.presentation.MovieGraphRoute
import com.martdev.flickq.feature.movie.presentation.movieGraph
import com.martdev.flickq.feature.showtime.presentation.ShowtimeListRoute
import com.martdev.flickq.feature.showtime.presentation.showtimeGraph
import kotlinx.serialization.Serializable

@Serializable
private data class PaymentRoute(val reservationId: Long)

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
            composable<PaymentRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<PaymentRoute>()
                PaymentPlaceholder(reservationId = route.reservationId)
            }
        }
    }
}

@Composable
private fun PaymentPlaceholder(reservationId: Long) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBackgroundBrush),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Payment",
            color = FlickQColors.Gold,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "COMING SOON · RESERVATION #$reservationId",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center
        )
    }
}
