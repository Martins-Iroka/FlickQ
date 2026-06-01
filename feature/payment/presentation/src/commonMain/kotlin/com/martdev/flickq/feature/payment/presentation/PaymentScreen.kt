package com.martdev.flickq.feature.payment.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.presentation.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PaymentRoot(
    reservationId: Long,
    onDone: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PaymentViewModel = koinViewModel { parametersOf(reservationId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            PaymentEvent.Done -> onDone()
            PaymentEvent.NavigateBack -> onNavigateBack()
        }
    }

    PaymentScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun PaymentScreen(
    state: PaymentState,
    onAction: (PaymentAction) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBackgroundBrush)
    ) {
        when {
            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Payment failed", color = FlickQColors.Gold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    text = state.error.asString(),
                    color = FlickQColors.Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                FlickQButton(
                    text = "Try again",
                    onClick = { onAction(PaymentAction.OnRetry) },
                    modifier = Modifier.padding(top = 16.dp)
                )
                TextButton(
                    onClick = { onAction(PaymentAction.OnBackClick) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(text = "Back to ticket", color = FlickQColors.GoldHighlight)
                }
            }

            state.phase == PaymentPhase.READY_TO_PAY -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ready to pay",
                    color = FlickQColors.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "You'll be taken to a secure checkout to complete your payment, then we'll confirm it automatically.",
                    color = FlickQColors.GoldHighlight,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
                FlickQButton(
                    text = "Proceed to payment",
                    onClick = { onAction(PaymentAction.OnProceedToPayment) },
                    modifier = Modifier.fillMaxWidth().width(320.dp)
                )
                TextButton(
                    onClick = { onAction(PaymentAction.OnBackClick) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(text = "Back to ticket", color = FlickQColors.GoldHighlight)
                }
            }

            state.phase == PaymentPhase.CONFIRMED -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Payment successful",
                    color = FlickQColors.Gold,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${state.amountLabel} paid",
                    color = FlickQColors.SeatAvailable,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = "REF · ${state.reference}",
                    color = FlickQColors.SeatAvailable,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 28.dp)
                )
                FlickQButton(
                    text = "Done",
                    onClick = { onAction(PaymentAction.OnDoneClick) },
                    modifier = Modifier.fillMaxWidth().width(320.dp)
                )
            }

            else -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = FlickQColors.Gold)
                Text(
                    text = when (state.phase) {
                        PaymentPhase.INITIALIZING -> "Initializing payment…"
                        PaymentPhase.AWAITING_PAYMENT -> "Complete payment in your browser — we'll confirm automatically…"
                        PaymentPhase.CONFIRMED -> "Confirming payment…"
                    },
                    color = FlickQColors.GoldHighlight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }
    }
}
