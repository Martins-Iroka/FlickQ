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
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.FlickQTheme
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.feature.auth.presentation.AuthGraphRoute
import com.martdev.flickq.feature.auth.presentation.authGraph
import kotlinx.serialization.Serializable

@Serializable
private data object MoviesRoute

@Composable
fun FlickQApp() {
    FlickQTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = AuthGraphRoute) {
            authGraph(
                navController = navController,
                onAuthenticated = {
                    navController.navigate(MoviesRoute) {
                        popUpTo(AuthGraphRoute) { inclusive = true }
                    }
                }
            )
            composable<MoviesRoute> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RoomBackgroundBrush),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "FlickQ",
                        color = FlickQColors.Gold,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                    Text(
                        text = "MOVIES COMING SOON",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
