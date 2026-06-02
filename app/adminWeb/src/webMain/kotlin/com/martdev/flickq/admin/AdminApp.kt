package com.martdev.flickq.admin

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.martdev.flickq.core.data.SessionManager
import com.martdev.flickq.core.designsystem.FlickQTheme
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.admin.presentation.AdminGraphRoute
import com.martdev.flickq.feature.admin.presentation.adminGraph
import org.koin.compose.koinInject

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
    }
}
