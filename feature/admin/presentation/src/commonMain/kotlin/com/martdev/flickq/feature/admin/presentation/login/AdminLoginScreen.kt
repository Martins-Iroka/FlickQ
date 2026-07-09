package com.martdev.flickq.feature.admin.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import com.martdev.flickq.feature.admin.presentation.logic.login.AdminLoginAction
import com.martdev.flickq.feature.admin.presentation.logic.login.AdminLoginEvent
import com.martdev.flickq.feature.admin.presentation.logic.login.AdminLoginState
import com.martdev.flickq.feature.admin.presentation.logic.login.AdminLoginViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdminLoginRoot(
    onAuthenticated: () -> Unit,
    viewModel: AdminLoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AdminLoginEvent.Authenticated -> onAuthenticated()
        }
    }

    AdminLoginScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun AdminLoginScreen(
    state: AdminLoginState,
    onAction: (AdminLoginAction) -> Unit
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
            text = "Admin console",
            color = FlickQColors.TicketPaper,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        val fieldModifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)

        FlickQTextField(
            value = state.email,
            onValueChange = { onAction(AdminLoginAction.OnEmailChange(it)) },
            label = "Email",
            modifier = fieldModifier,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        FlickQTextField(
            value = state.password,
            onValueChange = { onAction(AdminLoginAction.OnPasswordChange(it)) },
            label = "Password",
            modifier = fieldModifier.padding(top = 12.dp),
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
            text = "Sign in",
            onClick = { onAction(AdminLoginAction.OnSubmit) },
            modifier = fieldModifier.padding(top = 24.dp),
            enabled = state.canSubmit,
            loading = state.isLoading
        )
    }
}
