package com.martdev.flickq.feature.showtime.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.showtime.presentation.ShowtimeUi
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ShowtimeListRoot(
    movieId: Long,
    onPickShowtime: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ShowtimeListViewModel = koinViewModel { parametersOf(movieId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ShowtimeListEvent.PickShowtime -> onPickShowtime(event.showtimeId)
            ShowtimeListEvent.NavigateBack -> onNavigateBack()
        }
    }

    ShowtimeListScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun ShowtimeListScreen(
    state: ShowtimeListState,
    onAction: (ShowtimeListAction) -> Unit
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
                    onClick = { onAction(ShowtimeListAction.OnRetry) },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onAction(ShowtimeListAction.OnBackClick) }) {
                            Text(text = "← Back", color = FlickQColors.GoldHighlight)
                        }
                        Text(
                            text = "Select a showtime",
                            color = FlickQColors.Gold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                if (state.showtimes.isEmpty()) {
                    item {
                        Text(
                            text = "No showtimes available for this movie yet.",
                            color = FlickQColors.SeatAvailable,
                            modifier = Modifier.padding(top = 32.dp)
                        )
                    }
                } else {
                    items(state.showtimes, key = { it.id }) { showtime ->
                        ShowtimeRow(
                            showtime = showtime,
                            onClick = { onAction(ShowtimeListAction.OnShowtimeClick(showtime.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowtimeRow(
    showtime: ShowtimeUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FlickQColors.SurfaceNavy)
            .border(1.dp, FlickQColors.OutlineNavy, RoundedCornerShape(12.dp))
            .clickable(enabled = showtime.selectable, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = showtime.timeLabel,
                color = if (showtime.selectable) FlickQColors.TicketPaper else FlickQColors.SeatAvailable,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${showtime.dateLabel} · ${showtime.screenLabel}",
                color = FlickQColors.SeatAvailable,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = if (showtime.selectable) showtime.priceLabel else "Unavailable",
            color = if (showtime.selectable) FlickQColors.Gold else FlickQColors.SeatAvailable,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
