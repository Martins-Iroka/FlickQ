package com.martdev.flickq.feature.admin.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
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
 * Placeholder origin for the admin deep links. On the web target, [bindToNavigation] only uses the
 * path portion of these patterns to drive `window.location` (the host must be present to satisfy
 * the deep-link URI parser but is otherwise ignored), so the address bar shows clean paths like
 * `/dashboard` and `/reservations/42`. On native targets the deep links are simply unused.
 */
private const val ADMIN_URI = "https://finn-unsmitten-raeann.ngrok-free.dev"

/**
 * The admin feature's nav graph: sign in (role-gated) → a dashboard hub that fans out to
 * reports and the catalog / reservation management screens. All screens reuse the slice-1
 * MVI + design-system patterns.
 *
 * Each destination declares a [navDeepLink] so that, on the web target, the browser address bar
 * reflects the current screen (and reloads / pasted links resolve back to it).
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
