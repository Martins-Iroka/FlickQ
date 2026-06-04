package com.martdev.flickq.feature.auth.presentation.otp

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.FlickQTextField
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.presentation.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun OtpVerifyRoot(
    email: String,
    emailId: String,
    registrationToken: String,
    onVerified: () -> Unit,
    viewModel: OtpVerifyViewModel = koinViewModel { parametersOf(email, emailId, registrationToken) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OtpVerifyEvent.Verified -> onVerified()
        }
    }

    OtpVerifyScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun OtpVerifyScreen(
    state: OtpVerifyState,
    onAction: (OtpVerifyAction) -> Unit
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
            text = "Verify your email",
            color = FlickQColors.Gold,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Enter the 6-digit code sent to ${state.email}",
            color = FlickQColors.TicketPaper,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        val fieldModifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)

        FlickQTextField(
            value = state.code,
            onValueChange = { onAction(OtpVerifyAction.OnCodeChange(it)) },
            label = "Verification code",
            modifier = fieldModifier,
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        state.info?.let {
            Text(
                text = it.asString(),
                color = FlickQColors.GoldHighlight,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        state.error?.let {
            Text(
                text = it.asString(),
                color = FlickQColors.Error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        FlickQButton(
            text = "Verify",
            onClick = { onAction(OtpVerifyAction.OnVerifyClick) },
            modifier = fieldModifier.padding(top = 24.dp),
            enabled = state.canSubmit,
            loading = state.isLoading
        )

        TextButton(
            onClick = { onAction(OtpVerifyAction.OnResendClick) },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(text = "Resend code", color = FlickQColors.GoldHighlight)
        }
    }
}
