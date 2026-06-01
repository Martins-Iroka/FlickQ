package com.martdev.flickq.admin

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.martdev.flickq.core.designsystem.FlickQTheme
import com.martdev.flickq.feature.admin.presentation.AdminGraphRoute
import com.martdev.flickq.feature.admin.presentation.adminGraph

@Composable
fun AdminApp() {
    FlickQTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = AdminGraphRoute) {
            adminGraph(navController = navController)
        }
    }
}
