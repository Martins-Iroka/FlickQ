package com.martdev.flickq.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.martdev.flickq.core.data.push.PushInstallationIdSource
import com.martdev.flickq.feature.booking.presentation.TicketRoute
import com.martdev.flickq.feature.movie.presentation.MovieDetailRoute
import com.martdev.flickq.feature.movie.presentation.MovieListRoute
import com.martdev.flickq.feature.notification.domain.PushDeepLink
import com.martdev.flickq.feature.notification.domain.RegisterDeviceUseCase
import com.martdev.flickq.feature.notification.presentation.PushDeepLinkDispatcher
import org.koin.compose.koinInject

/**
 * While signed in and in the foreground: registers the current FID, then every new or rotated
 * one. Re-runs on each foreground. The use case's 24 h debounce and mutex make repeats cheap.
 */
@Composable
fun RegisterDeviceEffect(isAuthenticated: Boolean) {
    val registerDevice = koinInject<RegisterDeviceUseCase>()
    val installationIds = koinInject<PushInstallationIdSource>()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(isAuthenticated, lifecycleOwner) {
        if (!isAuthenticated) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            registerDevice(installationIds.lastKnown())
            installationIds.updates.collect { registerDevice(it) }
        }
    }
}

/**
 * Navigates to a tapped notification's target once signed in. A tap while signed out waits in
 * the dispatcher (up to its TTL) and is replayed when [isAuthenticated] flips after login.
 * The movie list stays underneath as the back destination.
 */
@Composable
fun PushDeepLinkEffect(navController: NavController, isAuthenticated: Boolean) {
    val dispatcher = koinInject<PushDeepLinkDispatcher>()
    val pending by dispatcher.pending.collectAsStateWithLifecycle()

    LaunchedEffect(pending, isAuthenticated) {
        if (pending == null || !isAuthenticated) return@LaunchedEffect
        val link = dispatcher.consume() ?: return@LaunchedEffect
        val route: Any = when (link) {
            is PushDeepLink.Ticket -> TicketRoute(link.reservationId)
            is PushDeepLink.Movie -> MovieDetailRoute(link.movieId)
        }
        navController.navigate(route) {
            popUpTo(MovieListRoute)
            launchSingleTop = true
        }
    }
}