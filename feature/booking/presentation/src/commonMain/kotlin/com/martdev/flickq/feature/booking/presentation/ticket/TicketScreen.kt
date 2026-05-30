package com.martdev.flickq.feature.booking.presentation.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.martdev.flickq.core.designsystem.MovieTicket
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.presentation.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TicketRoot(
    reservationId: Long,
    onProceedToPayment: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TicketViewModel = koinViewModel { parametersOf(reservationId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is TicketEvent.ProceedToPayment -> onProceedToPayment(event.reservationId)
            TicketEvent.NavigateBack -> onNavigateBack()
        }
    }

    TicketScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun TicketScreen(
    state: TicketState,
    onAction: (TicketAction) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBackgroundBrush)
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = FlickQColors.Gold,
                modifier = Modifier.align(Alignment.Center)
            )

            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = state.error.asString(), color = FlickQColors.Error)
                FlickQButton(
                    text = "Retry",
                    onClick = { onAction(TicketAction.OnRetry) },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            state.ticket != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Reservation confirmed",
                    color = FlickQColors.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${state.ticket.totalLabel} · payment pending",
                    color = FlickQColors.SeatAvailable,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                MovieTicket(
                    movieTitle = state.ticket.movieTitle,
                    runtimeLabel = state.ticket.runtimeLabel,
                    seatLabel = state.ticket.seatLabel,
                    dateLabel = state.ticket.dateLabel,
                    hallTimeLabel = state.ticket.hallTimeLabel,
                    code = state.ticket.code,
                    modifier = Modifier.fillMaxWidth().width(340.dp)
                )

                FlickQButton(
                    text = "Proceed to payment",
                    onClick = { onAction(TicketAction.OnProceedToPayment) },
                    modifier = Modifier.fillMaxWidth().width(340.dp).padding(top = 28.dp)
                )
                TextButton(
                    onClick = { onAction(TicketAction.OnBackClick) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(text = "Back to movies", color = FlickQColors.GoldHighlight)
                }
            }
        }
    }
}
