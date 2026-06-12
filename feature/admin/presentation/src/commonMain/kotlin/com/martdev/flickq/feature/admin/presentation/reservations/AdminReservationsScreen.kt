package com.martdev.flickq.feature.admin.presentation.reservations

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martdev.flickq.core.designsystem.AdminEmpty
import com.martdev.flickq.core.designsystem.AdminError
import com.martdev.flickq.core.designsystem.AdminLoading
import com.martdev.flickq.core.designsystem.AdminScaffold
import com.martdev.flickq.core.designsystem.DataColumn
import com.martdev.flickq.core.designsystem.DataTable
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationsAction
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationsState
import com.martdev.flickq.feature.admin.presentation.logic.reservations.AdminReservationsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdminReservationsRoot(
    onBack: () -> Unit,
    onOpenReservation: (Long) -> Unit,
    viewModel: AdminReservationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdminReservationsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, onOpenReservation = onOpenReservation)
}

@Composable
fun AdminReservationsScreen(
    state: AdminReservationsState,
    onAction: (AdminReservationsAction) -> Unit,
    onBack: () -> Unit,
    onOpenReservation: (Long) -> Unit,
) {
    AdminScaffold(title = "Reservations", onBack = onBack) {
        val error = state.error
        when {
            state.isLoading -> AdminLoading()
            error != null -> AdminError(message = error.asString(), onRetry = { onAction(AdminReservationsAction.OnRetry) })
            state.reservations.isEmpty() -> AdminEmpty(message = "No reservations yet.", modifier = Modifier.padding(24.dp))
            else -> DataTable(
                items = state.reservations,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                onRowClick = { onOpenReservation(it.id) },
                columns = listOf(
                    DataColumn("ID", 0.25f) { it.id.toString() },
                    DataColumn("Showtime", 0.4f) { it.showtimeId.toString() },
                    DataColumn("Status", 0.5f) { it.status.name },
                    DataColumn("Seats", 0.3f) { it.seats.size.toString() },
                ),
            )
        }
    }
}
