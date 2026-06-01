package com.martdev.flickq.feature.admin.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.martdev.flickq.feature.admin.presentation.login.AdminLoginRoot
import com.martdev.flickq.feature.admin.presentation.reports.AdminReportsRoot
import kotlinx.serialization.Serializable

@Serializable
data object AdminGraphRoute

@Serializable
data object AdminLoginRoute

@Serializable
data object AdminDashboardRoute

/**
 * The admin feature's nav graph: sign in (role-gated) → dashboard/reports. CRUD,
 * reservations and payments screens are added under the dashboard incrementally.
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
            AdminReportsRoot()
        }
    }
}
