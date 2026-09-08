package com.martdev.flickq.feature.reservation.presentation.myreservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.PosterImage
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.designsystem.formatNaira
import com.martdev.flickq.feature.reservation.presentation.ReservationTicketUI
import com.martdev.flickq.reservation.model.ReservationStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyReservationScreen(
    viewModel: MyReservationViewModel = koinViewModel()
) {

    val state by viewModel.state.collectAsStateWithLifecycle()

    MyReservationCompose(state)
}

@Composable
internal fun MyReservationCompose(
    state: ReservationListState
) {
    Column(modifier = Modifier.fillMaxSize()
        .background(RoomBackgroundBrush)) {

        Text(
            text = "My Reservation",
            color = FlickQColors.Gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        onClick = {},
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(state.reservations, key = { it.id }) {
                            ReservationCard(
                                it
                            )
                        }

                        if (state.isLoadingMore || state.canLoadMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(color = FlickQColors.Gold)
                                    } else {
                                        FlickQButton(
                                            text = "Load more",
                                            onClick = {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(
    reservationTicketUI: ReservationTicketUI
) {
    val statusColor = when(reservationTicketUI.status) {
        ReservationStatus.PENDING -> FlickQColors.GoldEdge
        ReservationStatus.CONFIRMED -> FlickQColors.Green
        ReservationStatus.CANCELLED -> FlickQColors.Error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
    ) {
        PosterImage(
            url = reservationTicketUI.posterUrl,
            contentDescription = "movie",
            modifier = Modifier
                .size(150.dp, 200.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Column(modifier = Modifier.fillMaxSize().padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = reservationTicketUI.movieTitle,
                color = FlickQColors.TicketPaper,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = formatNaira(reservationTicketUI.totalAmount),
                color = FlickQColors.TicketPaper,
                fontSize = 18.sp
            )

            Text(
                text = reservationTicketUI.status.toString(),
                color = statusColor,
                fontSize = 16.sp
            )
            
            if (reservationTicketUI.status == ReservationStatus.PENDING) {
                Text(
                    text = "Expires at ${reservationTicketUI.timeExpiration}",
                    color = FlickQColors.Error
                )

                FlickQButton(
                    text = "Pay",
                    onClick = {},
                )
            }
        }
    }
}