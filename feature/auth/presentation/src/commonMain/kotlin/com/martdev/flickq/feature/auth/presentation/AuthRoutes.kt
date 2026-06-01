package com.martdev.flickq.feature.auth.presentation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.martdev.flickq.feature.auth.presentation.login.LoginRoot
import com.martdev.flickq.feature.auth.presentation.otp.OtpVerifyRoot
import com.martdev.flickq.feature.auth.presentation.register.RegisterRoot
import kotlinx.serialization.Serializable

@Serializable
data object AuthGraphRoute

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data class OtpVerifyRoute(val emailId: String, val registrationToken: String)

/**
 * The auth feature's nav graph. [onAuthenticated] is the single cross-feature exit,
 * fired once the user has a valid session (login or OTP verification).
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthenticated: () -> Unit
) {
    navigation<AuthGraphRoute>(startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginRoot(
                onAuthenticated = onAuthenticated,
                onNavigateToRegister = { navController.navigate(RegisterRoute) }
            )
        }
        composable<RegisterRoute> {
            RegisterRoot(
                onRegistered = { emailId, token ->
                    navController.navigate(OtpVerifyRoute(emailId, token))
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable<OtpVerifyRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<OtpVerifyRoute>()
            OtpVerifyRoot(
                emailId = route.emailId,
                registrationToken = route.registrationToken,
                // Verification issues no session — return to login so the user signs in.
                onVerified = {
                    navController.navigate(LoginRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }
    }
}
