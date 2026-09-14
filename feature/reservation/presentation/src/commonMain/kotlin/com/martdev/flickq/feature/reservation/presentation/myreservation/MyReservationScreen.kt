package com.martdev.flickq.feature.reservation.presentation.myreservation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.PosterImage
import com.martdev.flickq.core.designsystem.RoomBackgroundBrush
import com.martdev.flickq.core.designsystem.formatNaira
import com.martdev.flickq.core.presentation.ObserveAsEvents
import com.martdev.flickq.feature.reservation.presentation.ReservationTicketUI
import com.martdev.flickq.reservation.model.ReservationStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyReservationScreen(
    viewModel: MyReservationViewModel = koinViewModel(),
    onPayClicked: (Long) -> Unit
) {

    val reservationList = viewModel.reservationList.collectAsLazyPagingItems()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.event) {
        when(it) {
            ReservationListEvent.NavigateToDetail -> TODO()
            is ReservationListEvent.NavigateToPayment -> onPayClicked(it.reservationId)
        }
    }
    MyReservationCompose(state,reservationList, viewModel::onAction)
}

@Composable
fun MyReservationCompose(
    state: ReservationListState,
    tickets: LazyPagingItems<ReservationTicketUI>,
    onAction: (ReservationListAction) -> Unit = {}
) {
    var showDialog by rememberSaveable {
        mutableStateOf(false)
    }
    Column(modifier = Modifier.fillMaxSize()
        .background(RoomBackgroundBrush)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Reservation",
                color = FlickQColors.Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )

            IconButton(onClick = {
                showDialog = true
            }) {
                Icon(imageVector = Icons.Filled.FilterAlt,
                    contentDescription = "",
                    tint = FlickQColors.Gold)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(count = tickets.itemCount,
                key = tickets.itemKey {
                    it.id
                }) {
                val item = tickets[it]
                item?.let { r ->
                    ReservationCard(
                        r,
                        onAction
                    )
                }
            }

            tickets.loadState.apply {
                val refreshState = refresh
                val appendState = append

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            refreshState is LoadState.Loading || appendState is LoadState.Loading -> {
                                CircularProgressIndicator(color = FlickQColors.Gold)
                            }
                            refreshState is LoadState.Error -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Error: ${refreshState.error.message}")
                                    FlickQButton(
                                        text = "Retry",
                                        onClick = { onAction(ReservationListAction.OnRetry) },
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                            }
                            appendState is LoadState.Error -> {
                                Text("Error: ${appendState.error.message}")
                            }
                        }
                    }
                }
            }
        }
        if (showDialog) {
            Dialog(onDismissRequest = { }) {
                Column(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    Text("Filter by status", color = Color.Black)

                    RadioButtonRow(
                        status = "ALL",
                        selected = state.status == "ALL"
                    ) {
                        showDialog = false
                        onAction(ReservationListAction.OnStatusSelected(null))
                    }

                    RadioButtonRow(
                        status = ReservationStatus.CONFIRMED.toString(),
                        selected = state.status == ReservationStatus.CONFIRMED.toString()
                    ) {
                        showDialog = false
                        onAction(ReservationListAction.OnStatusSelected(ReservationStatus.CONFIRMED.toString()))
                    }
                    RadioButtonRow(
                        status = ReservationStatus.CANCELLED.toString(),
                        selected = state.status == ReservationStatus.CANCELLED.toString()
                    ) {
                        showDialog = false
                        onAction(ReservationListAction.OnStatusSelected(ReservationStatus.CANCELLED.toString()))
                    }
                    RadioButtonRow(
                        status = ReservationStatus.PENDING.toString(),
                        selected = state.status == ReservationStatus.PENDING.toString()
                    ) {
                        showDialog = false
                        onAction(ReservationListAction.OnStatusSelected(ReservationStatus.PENDING.toString()))
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioButtonRow(
    status: String = "Selected",
    selected: Boolean = true,
    select: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            select()
        }.padding(2.dp)) {
        RadioButton(
            selected = selected,
            onClick = select
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(status, color = Color.Black)
    }
}

@Composable
private fun ReservationCard(
    reservationTicketUI: ReservationTicketUI,
    onAction: (ReservationListAction) -> Unit
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
                    onClick = {
                        onAction(ReservationListAction.OnPayForPendingReservation(reservationTicketUI.id, reservationTicketUI.expiresAt))
                    },
                )
            }
        }
    }
}