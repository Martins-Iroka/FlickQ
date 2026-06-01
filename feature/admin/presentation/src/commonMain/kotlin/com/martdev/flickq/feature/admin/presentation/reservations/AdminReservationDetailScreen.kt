package com.martdev.flickq.feature.admin.presentation.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.AdminError
import com.martdev.flickq.core.designsystem.AdminLoading
import com.martdev.flickq.core.designsystem.AdminScaffold
import com.martdev.flickq.core.designsystem.ConfirmDialog
import com.martdev.flickq.core.designsystem.FlickQButton
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.reservation.model.Reservation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AdminReservationDetailRoot(
    reservationId: Long,
    onBack: () -> Unit,
    viewModel: AdminReservationDetailViewModel = koinViewModel { parametersOf(reservationId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdminReservationDetailScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun AdminReservationDetailScreen(
    state: AdminReservationDetailState,
    onAction: (AdminReservationDetailAction) -> Unit,
    onBack: () -> Unit,
) {
    AdminScaffold(title = "Reservation", onBack = onBack) {
        when {
            state.isLoading -> AdminLoading()
            state.error != null -> AdminError(message = state.error.asString(), onRetry = { onAction(AdminReservationDetailAction.OnRetry) })
            state.reservation != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.message?.let { message ->
                    item { Text(text = message.asString(), color = FlickQColors.GoldHighlight, fontSize = 13.sp) }
                }
                item { ReservationCard(state.reservation) }
                item {
                    Text(text = "Payments", color = FlickQColors.GoldHighlight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                if (state.payments.isEmpty()) {
                    item { Text(text = "No payment attempts recorded.", color = FlickQColors.SeatAvailable, fontSize = 13.sp) }
                } else {
                    items(state.payments) { payment -> PaymentRow(payment) }
                }
                if (state.canCancel) {
                    item {
                        FlickQButton(
                            text = if (state.isCancelling) "Cancelling…" else "Cancel reservation",
                            onClick = { onAction(AdminReservationDetailAction.OnCancelClick) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            enabled = !state.isCancelling,
                        )
                    }
                }
            }
        }
    }

    if (state.showCancelConfirm) {
        ConfirmDialog(
            title = "Cancel reservation?",
            message = "This releases the held seats and marks the reservation cancelled. This can't be undone.",
            confirmLabel = "Cancel reservation",
            onConfirm = { onAction(AdminReservationDetailAction.OnConfirmCancel) },
            onDismiss = { onAction(AdminReservationDetailAction.OnDismissCancel) },
        )
    }
}

@Composable
private fun ReservationCard(reservation: Reservation) {
    Column(
        modifier = Modifier.fillMaxWidth().background(FlickQColors.DeepNavy, RoundedCornerShape(14.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Field("Reservation", "#${reservation.id}")
        Field("Showtime", reservation.showtimeId.toString())
        Field("User", reservation.userId.toString())
        Field("Status", reservation.status.name)
        Field("Total", "NGN ${reservation.totalAmount}")
        Field("Seats", reservation.seats.joinToString { it.seatId.toString() }.ifBlank { "—" })
    }
}

@Composable
private fun PaymentRow(payment: Payment) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FlickQColors.SurfaceNavy, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = payment.reference.ifBlank { "—" }, color = FlickQColors.TicketPaper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = "${payment.currency} ${payment.amount}", color = FlickQColors.SeatAvailable, fontSize = 12.sp)
        }
        Text(text = payment.status.name, color = FlickQColors.GoldHighlight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = FlickQColors.SeatAvailable, fontSize = 14.sp)
        Text(text = value, color = FlickQColors.TicketPaper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
