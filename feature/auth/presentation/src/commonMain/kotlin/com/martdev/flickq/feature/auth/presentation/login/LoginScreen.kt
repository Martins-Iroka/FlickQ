package com.martdev.flickq.feature.auth.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.FlickQTextField
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.presentation.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
//Nana12345
@Composable
fun LoginRoot(
    onAuthenticated: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToVerify: (email: String) -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            LoginEvent.Authenticated -> onAuthenticated()
            LoginEvent.NavigateToRegister -> onNavigateToRegister()
            is LoginEvent.NavigateToVerify -> onNavigateToVerify(event.email)
        }
    }

    LoginScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun LoginScreen(
    state: LoginState,
    onAction: (LoginAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBackgroundBrush)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FlickQ",
            color = FlickQColors.Gold,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
        Text(
            text = "Welcome back",
            color = FlickQColors.TicketPaper,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        val fieldModifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)

        FlickQTextField(
            value = state.email,
            onValueChange = { onAction(LoginAction.OnEmailChange(it)) },
            label = "Email",
            modifier = fieldModifier,
            isError = state.emailError,
            supportingText = if (state.emailError) "Enter a valid email" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        FlickQTextField(
            value = state.password,
            onValueChange = { onAction(LoginAction.OnPasswordChange(it)) },
            label = "Password",
            modifier = fieldModifier.padding(top = 12.dp),
            isError = state.passwordError,
            supportingText = if (state.passwordError) "At least 6 characters" else null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        state.error?.let {
            Text(
                text = it.asString(),
                color = FlickQColors.Error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        FlickQButton(
            text = "Log in",
            onClick = { onAction(LoginAction.OnLoginClick) },
            modifier = fieldModifier.padding(top = 24.dp),
            enabled = state.canSubmit,
            loading = state.isLoading
        )

        TextButton(
            onClick = { onAction(LoginAction.OnRegisterClick) },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = "New here? Create an account",
                color = FlickQColors.GoldHighlight
            )
        }
    }
}
