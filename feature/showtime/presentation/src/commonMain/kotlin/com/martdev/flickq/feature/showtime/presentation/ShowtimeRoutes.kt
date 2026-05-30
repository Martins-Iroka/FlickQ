package com.martdev.flickq.feature.showtime.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.martdev.flickq.feature.showtime.presentation.list.ShowtimeListRoot
import kotlinx.serialization.Serializable

@Serializable
data class ShowtimeListRoute(val movieId: Long)

/**
 * The showtime feature's nav graph: list a movie's showtimes and pick one.
 * Picking a showtime is the cross-feature exit ([onPickShowtime], wired to booking).
 */
fun NavGraphBuilder.showtimeGraph(
    navController: NavController,
    onPickShowtime: (Long) -> Unit
) {
    composable<ShowtimeListRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ShowtimeListRoute>()
        ShowtimeListRoot(
            movieId = route.movieId,
            onPickShowtime = onPickShowtime,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
