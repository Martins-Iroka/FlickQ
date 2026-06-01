package com.martdev.flickq.feature.admin.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.martdev.flickq.feature.admin.presentation.genres.AdminGenresRoot
import com.martdev.flickq.feature.admin.presentation.hub.AdminHubScreen
import com.martdev.flickq.feature.admin.presentation.login.AdminLoginRoot
import com.martdev.flickq.feature.admin.presentation.movies.AdminMoviesRoot
import com.martdev.flickq.feature.admin.presentation.reports.AdminReportsRoot
import com.martdev.flickq.feature.admin.presentation.reservations.AdminReservationDetailRoot
import com.martdev.flickq.feature.admin.presentation.reservations.AdminReservationsRoot
import com.martdev.flickq.feature.admin.presentation.rooms.AdminRoomsRoot
import com.martdev.flickq.feature.admin.presentation.showtimes.AdminShowtimesRoot
import kotlinx.serialization.Serializable

@Serializable
data object AdminGraphRoute

@Serializable
data object AdminLoginRoute

@Serializable
data object AdminDashboardRoute

@Serializable
data object AdminReportsRoute

@Serializable
data object AdminMoviesRoute

@Serializable
data object AdminGenresRoute

@Serializable
data object AdminRoomsRoute

@Serializable
data object AdminShowtimesRoute

@Serializable
data object AdminReservationsRoute

@Serializable
data class AdminReservationDetailRoute(val reservationId: Long)

/**
 * The admin feature's nav graph: sign in (role-gated) → a dashboard hub that fans out to
 * reports and the catalog / reservation management screens. All screens reuse the slice-1
 * MVI + design-system patterns.
 */
fun NavGraphBuilder.adminGraph(navController: NavController) {
    navigation<AdminGraphRoute>(startDestination = AdminLoginRoute) {
        composable<AdminLoginRoute> {
            AdminLoginRoot(
                onAuthenticated = {
                    navController.navigate(AdminDashboardRoute) {
                        popUpTo(AdminLoginRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<AdminDashboardRoute> {
            AdminHubScreen(
                onOpenReports = { navController.navigate(AdminReportsRoute) },
                onOpenMovies = { navController.navigate(AdminMoviesRoute) },
                onOpenGenres = { navController.navigate(AdminGenresRoute) },
                onOpenRooms = { navController.navigate(AdminRoomsRoute) },
                onOpenShowtimes = { navController.navigate(AdminShowtimesRoute) },
                onOpenReservations = { navController.navigate(AdminReservationsRoute) },
            )
        }
        composable<AdminReportsRoute> { AdminReportsRoot(onBack = navController::navigateUp) }
        composable<AdminMoviesRoute> { AdminMoviesRoot(onBack = navController::navigateUp) }
        composable<AdminGenresRoute> { AdminGenresRoot(onBack = navController::navigateUp) }
        composable<AdminRoomsRoute> { AdminRoomsRoot(onBack = navController::navigateUp) }
        composable<AdminShowtimesRoute> { AdminShowtimesRoot(onBack = navController::navigateUp) }
        composable<AdminReservationsRoute> {
            AdminReservationsRoot(
                onBack = navController::navigateUp,
                onOpenReservation = { id -> navController.navigate(AdminReservationDetailRoute(id)) },
            )
        }
        composable<AdminReservationDetailRoute> { entry ->
            val route = entry.toRoute<AdminReservationDetailRoute>()
            AdminReservationDetailRoot(reservationId = route.reservationId, onBack = navController::navigateUp)
        }
    }
}
