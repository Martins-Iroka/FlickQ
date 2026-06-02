package com.martdev.flickq.feature.movie.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.martdev.flickq.feature.movie.presentation.detail.MovieDetailRoot
import com.martdev.flickq.feature.movie.presentation.list.MovieListRoot
import kotlinx.serialization.Serializable

@Serializable
data object MovieGraphRoute

@Serializable
data object MovieListRoute

@Serializable
data class MovieDetailRoute(val movieId: Long)

/**
 * The movie feature's nav graph: browse the catalog and open a detail page.
 * List -> detail is intra-feature; [onViewShowtimes] is the cross-feature exit
 * (wired to the showtime feature in :app:shared).
 */
fun NavGraphBuilder.movieGraph(
    navController: NavController,
    onViewShowtimes: (Long) -> Unit,
    onLogout: () -> Unit,
) {
    navigation<MovieGraphRoute>(startDestination = MovieListRoute) {
        composable<MovieListRoute> {
            MovieListRoot(
                onMovieClick = { movieId -> navController.navigate(MovieDetailRoute(movieId)) },
                onLogout = onLogout,
            )
        }
        composable<MovieDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<MovieDetailRoute>()
            MovieDetailRoot(
                movieId = route.movieId,
                onNavigateBack = { navController.popBackStack() },
                onViewShowtimes = onViewShowtimes
            )
        }
    }
}
