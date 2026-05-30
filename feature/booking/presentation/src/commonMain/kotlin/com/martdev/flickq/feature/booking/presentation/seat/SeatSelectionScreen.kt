package com.martdev.flickq.feature.booking.presentation.seat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.designsystem.SeatMap
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.booking.presentation.formatNaira
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SeatSelectionRoot(
    showtimeId: Long,
    onReserved: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SeatSelectionViewModel = koinViewModel { parametersOf(showtimeId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SeatSelectionEvent.ReservationCreated -> onReserved(event.reservationId)
            SeatSelectionEvent.NavigateBack -> onNavigateBack()
        }
    }

    SeatSelectionScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun SeatSelectionScreen(
    state: SeatSelectionState,
    onAction: (SeatSelectionAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBackgroundBrush)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onAction(SeatSelectionAction.OnBackClick) }) {
                Text(text = "← Back", color = FlickQColors.GoldHighlight)
            }
            Text(
                text = "Select your seats",
                color = FlickQColors.Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    color = FlickQColors.Gold,
                    modifier = Modifier.align(Alignment.Center)
                )

                state.error != null && state.seats.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = state.error.asString(), color = FlickQColors.Error)
                    FlickQButton(
                        text = "Retry",
                        onClick = { onAction(SeatSelectionAction.OnRetry) },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                else -> SeatMap(
                    rows = state.rows,
                    columns = state.columns,
                    seats = state.seats,
                    selectedIds = state.selectedIds,
                    onSeatClick = { onAction(SeatSelectionAction.OnSeatClick(it)) },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                )
            }
        }

        SeatLegend(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))

        ReserveBar(
            selectedCount = state.selectedCount,
            totalLabel = formatNaira(state.totalAmount),
            canReserve = state.canReserve,
            isReserving = state.isReserving,
            error = state.error?.asString()?.takeIf { state.seats.isNotEmpty() },
            onReserve = { onAction(SeatSelectionAction.OnReserveClick) }
        )
    }
}

@Composable
private fun SeatLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(color = FlickQColors.SeatAvailable, label = "Available")
        LegendDot(color = FlickQColors.Gold, label = "Selected")
        LegendDot(color = FlickQColors.SeatOccupied, label = "Taken")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = FlickQColors.SeatAvailable, fontSize = 12.sp)
    }
}

@Composable
private fun ReserveBar(
    selectedCount: Int,
    totalLabel: String,
    canReserve: Boolean,
    isReserving: Boolean,
    error: String?,
    onReserve: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FlickQColors.SurfaceNavy)
            .padding(20.dp)
    ) {
        if (error != null) {
            Text(
                text = error,
                color = FlickQColors.Error,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (selectedCount == 0) "No seats selected" else "$selectedCount seat(s)",
                    color = FlickQColors.SeatAvailable,
                    fontSize = 12.sp
                )
                Text(
                    text = totalLabel,
                    color = FlickQColors.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
            FlickQButton(
                text = "Reserve",
                onClick = onReserve,
                enabled = canReserve,
                loading = isReserving
            )
        }
    }
}
