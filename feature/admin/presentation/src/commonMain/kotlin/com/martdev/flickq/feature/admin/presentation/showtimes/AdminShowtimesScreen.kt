package com.martdev.flickq.feature.admin.presentation.showtimes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.AdminEmpty
import com.martdev.flickq.core.designsystem.AdminError
import com.martdev.flickq.core.designsystem.AdminLoading
import com.martdev.flickq.core.designsystem.AdminScaffold
import com.martdev.flickq.core.designsystem.ConfirmDialog
import com.martdev.flickq.core.designsystem.DataColumn
import com.martdev.flickq.core.designsystem.DataTable
import com.martdev.flickq.core.designsystem.FlickQColors
import com.martdev.flickq.core.designsystem.FlickQFormDialog
import com.martdev.flickq.core.designsystem.FlickQTextField
import com.martdev.flickq.core.designsystem.RowAction
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesAction
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesState
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesViewModel
import com.martdev.flickq.showtime.model.ShowtimeStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdminShowtimesRoot(
    onBack: () -> Unit,
    viewModel: AdminShowtimesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdminShowtimesScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun AdminShowtimesScreen(
    state: AdminShowtimesState,
    onAction: (AdminShowtimesAction) -> Unit,
    onBack: () -> Unit,
) {
    AdminScaffold(
        title = "Showtimes",
        onBack = onBack,
        actions = { RowAction(label = "+ Add", onClick = { onAction(AdminShowtimesAction.OnAddClick) }) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.message?.let { message ->
                Text(
                    text = message.asString(),
                    color = FlickQColors.GoldHighlight,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            val error = state.error
            when {
                state.isLoading -> AdminLoading()
                error != null -> AdminError(message = error.asString(), onRetry = { onAction(AdminShowtimesAction.OnRetry) })
                state.showtimes.isEmpty() -> AdminEmpty(message = "No showtimes yet. Tap “+ Add” to schedule one.", modifier = Modifier.padding(24.dp))
                else -> DataTable(
                    items = state.showtimes,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    columns = listOf(
                        DataColumn("ID", 0.25f) { it.id.toString() },
                        DataColumn("Movie", 0.4f) { it.movieId.toString() },
                        DataColumn("Room", 0.4f) { it.roomId.toString() },
                        DataColumn("Status", 0.6f) { it.status.name },
                    ),
                    rowActions = { showtime ->
                        RowAction(label = "Edit", onClick = { onAction(AdminShowtimesAction.OnEditClick(showtime)) })
                        RowAction(label = "Status", onClick = { onAction(AdminShowtimesAction.OnStatusClick(showtime)) })
                        RowAction(label = "Seats", onClick = { onAction(AdminShowtimesAction.OnPopulateClick(showtime)) })
                        RowAction(label = "Delete", onClick = { onAction(AdminShowtimesAction.OnDeleteClick(showtime)) }, tint = FlickQColors.Error)
                    },
                    canLoadMore = state.canLoadMore,
                    isLoadingMore = state.isLoadingMore,
                    onLoadMore = { onAction(AdminShowtimesAction.OnLoadMore) },
                )
            }
        }
    }

    state.form?.let { form ->
        FlickQFormDialog(
            title = if (form.editingId == null) "New showtime" else "Edit showtime",
            confirmLabel = if (state.isSaving) "Saving…" else "Save",
            confirmEnabled = form.isValid && !state.isSaving,
            onConfirm = { onAction(AdminShowtimesAction.OnSave) },
            onDismiss = { onAction(AdminShowtimesAction.OnDismissDialog) },
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlickQTextField(
                    form.movieId, { onAction(AdminShowtimesAction.OnMovieIdChange(it)) }, "Movie ID",
                    Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlickQTextField(
                    form.roomId, { onAction(AdminShowtimesAction.OnRoomIdChange(it)) }, "Room ID",
                    Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlickQTextField(form.startsAt, { onAction(AdminShowtimesAction.OnStartsAtChange(it)) }, "Starts at (ISO-8601)", Modifier.fillMaxWidth())
                FlickQTextField(form.endsAt, { onAction(AdminShowtimesAction.OnEndsAtChange(it)) }, "Ends at (ISO-8601)", Modifier.fillMaxWidth())
                FlickQTextField(
                    form.price, { onAction(AdminShowtimesAction.OnPriceChange(it)) }, "Price",
                    Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                state.dialogError?.let { Text(text = it.asString(), color = FlickQColors.Error, fontSize = 12.sp) }
            }
        }
    }

    state.deleting?.let { showtime ->
        ConfirmDialog(
            title = "Delete showtime?",
            message = "Showtime ${showtime.id} will be removed. Showtimes with reservations can't be deleted.",
            confirmLabel = "Delete",
            onConfirm = { onAction(AdminShowtimesAction.OnConfirmDelete) },
            onDismiss = { onAction(AdminShowtimesAction.OnDismissDelete) },
        )
    }

    state.statusFor?.let { showtime ->
        AlertDialog(
            onDismissRequest = { onAction(AdminShowtimesAction.OnDismissStatus) },
            containerColor = FlickQColors.DeepNavy,
            title = { Text(text = "Set status", color = FlickQColors.Gold, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ShowtimeStatus.entries.forEach { status ->
                        Text(
                            text = status.name,
                            color = if (status == showtime.status) FlickQColors.Gold else FlickQColors.TicketPaper,
                            fontWeight = if (status == showtime.status) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { onAction(AdminShowtimesAction.OnStatusPicked(status)) }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onAction(AdminShowtimesAction.OnDismissStatus) }) {
                    Text(text = "Close", color = FlickQColors.SeatAvailable)
                }
            },
        )
    }

    state.populatingFor?.let { showtime ->
        ConfirmDialog(
            title = "Populate seats?",
            message = "Seed the showtime-seat grid for showtime ${showtime.id} from its room layout.",
            confirmLabel = "Populate",
            destructive = false,
            onConfirm = { onAction(AdminShowtimesAction.OnConfirmPopulate) },
            onDismiss = { onAction(AdminShowtimesAction.OnDismissPopulate) },
        )
    }
}
