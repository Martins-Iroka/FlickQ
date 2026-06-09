package com.martdev.flickq.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.martdev.flickq.core.data.SessionManager
import com.martdev.flickq.core.designsystem.FlickQTheme
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.admin.presentation.AdminGraphRoute
import com.martdev.flickq.feature.admin.presentation.adminGraph
import org.koin.compose.koinInject

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
fun AdminApp() {
    FlickQTheme {
        val navController = rememberNavController()
        val sessionManager = koinInject<SessionManager>()
        // Refresh token expired/revoked → clear the stack and send the admin back to login.
        ObserveAsEvents(sessionManager.events) {
            navController.navigate(AdminGraphRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        NavHost(navController = navController, startDestination = AdminGraphRoute) {
            adminGraph(navController = navController)
        }
        // Keep the browser address bar in sync with the nav back stack: navigating updates the URL
        // (e.g. /dashboard), and Back/Forward + reloads drive the NavController via the per-route
        // deep links declared in adminGraph(). Web-only, hence the webMain placement.
        LaunchedEffect(navController) {
            navController.bindToBrowserNavigation { entry ->
                val route = entry.destination.route.orEmpty()
                when {
                    route.endsWith("AdminDashboardRoute")    -> "dashboard"
                    route.endsWith("AdminLoginRoute")        -> "login"
                    route.endsWith("AdminMoviesRoute")       -> "movies"
                    route.endsWith("AdminReservationsRoute") -> "reservations"
                    route.endsWith("AdminReportsRoute") -> "report"
                    route.endsWith("AdminGenresRoute") -> "genre"
                    route.endsWith("AdminRoomsRoute") -> "room"
                    route.endsWith("AdminShowtimesRoute") -> "showtime"
                    route.endsWith("AdminReservationDetailRoute/{reservationId}") ->  "reservation-detail"
                    // ...one branch per route in AdminRoutes.kt
                    else -> ""
                }
            }
        }
    }
}
