package com.martdev.flickq.feature.admin.presentation.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminRoomsAction
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminRoomsState
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminRoomsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdminRoomsRoot(
    onBack: () -> Unit,
    viewModel: AdminRoomsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdminRoomsScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun AdminRoomsScreen(
    state: AdminRoomsState,
    onAction: (AdminRoomsAction) -> Unit,
    onBack: () -> Unit,
) {
    AdminScaffold(
        title = "Rooms",
        onBack = onBack,
        actions = { RowAction(label = "+ Add", onClick = { onAction(AdminRoomsAction.OnAddClick) }) },
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
                error != null -> AdminError(message = error.asString(), onRetry = { onAction(AdminRoomsAction.OnRetry) })
                state.rooms.isEmpty() -> AdminEmpty(message = "No rooms yet. Tap “+ Add” to create one.", modifier = Modifier.padding(24.dp))
                else -> DataTable(
                    items = state.rooms,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    columns = listOf(
                        DataColumn("Name", 1f) { it.name },
                        DataColumn("Layout", 0.6f) { "${it.rows}×${it.columns}" },
                    ),
                    rowActions = { room ->
                        RowAction(label = "Edit", onClick = { onAction(AdminRoomsAction.OnEditClick(room)) })
                        RowAction(label = "Seats", onClick = { onAction(AdminRoomsAction.OnGenerateSeatsClick(room)) })
                        RowAction(label = "Delete", onClick = { onAction(AdminRoomsAction.OnDeleteClick(room)) }, tint = FlickQColors.Error)
                    },
                )
            }
        }
    }

    state.form?.let { form ->
        FlickQFormDialog(
            title = if (form.editingId == null) "New room" else "Edit room",
            confirmLabel = if (state.isSaving) "Saving…" else "Save",
            confirmEnabled = form.isValid && !state.isSaving,
            onConfirm = { onAction(AdminRoomsAction.OnSave) },
            onDismiss = { onAction(AdminRoomsAction.OnDismissDialog) },
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlickQTextField(form.name, { onAction(AdminRoomsAction.OnNameChange(it)) }, "Room name", Modifier.fillMaxWidth())
                FlickQTextField(
                    form.rows, { onAction(AdminRoomsAction.OnRowsChange(it)) }, "Rows",
                    Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlickQTextField(
                    form.columns, { onAction(AdminRoomsAction.OnColumnsChange(it)) }, "Columns",
                    Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                state.dialogError?.let { Text(text = it.asString(), color = FlickQColors.Error, fontSize = 12.sp) }
            }
        }
    }

    state.deleting?.let { room ->
        ConfirmDialog(
            title = "Delete room?",
            message = "“${room.name}” will be removed. Rooms with scheduled showtimes can't be deleted.",
            confirmLabel = "Delete",
            onConfirm = { onAction(AdminRoomsAction.OnConfirmDelete) },
            onDismiss = { onAction(AdminRoomsAction.OnDismissDelete) },
        )
    }

    state.seatingFor?.let { room ->
        ConfirmDialog(
            title = "Generate seats?",
            message = "This creates ${room.rows * room.columns} seats (${room.rows}×${room.columns}) for “${room.name}”.",
            confirmLabel = "Generate",
            destructive = false,
            onConfirm = { onAction(AdminRoomsAction.OnConfirmGenerateSeats) },
            onDismiss = { onAction(AdminRoomsAction.OnDismissGenerateSeats) },
        )
    }
}
