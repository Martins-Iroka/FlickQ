package com.martdev.flickq.feature.admin.presentation.genres

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.martdev.flickq.movie.model.Genre
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdminGenresRoot(
    onBack: () -> Unit,
    viewModel: AdminGenresViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdminGenresScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun AdminGenresScreen(
    state: AdminGenresState,
    onAction: (AdminGenresAction) -> Unit,
    onBack: () -> Unit,
) {
    AdminScaffold(
        title = "Genres",
        onBack = onBack,
        actions = { RowAction(label = "+ Add", onClick = { onAction(AdminGenresAction.OnAddClick) }) },
    ) {
        when {
            state.isLoading -> AdminLoading()
            state.error != null -> AdminError(message = state.error.asString(), onRetry = { onAction(AdminGenresAction.OnRetry) })
            state.genres.isEmpty() -> AdminEmpty(message = "No genres yet. Tap “+ Add” to create one.", modifier = Modifier.padding(24.dp))
            else -> DataTable(
                items = state.genres,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                columns = listOf(
                    DataColumn("ID", 0.3f) { it.id.toString() },
                    DataColumn("Name", 1f) { it.name },
                ),
                rowActions = { genre ->
                    RowAction(label = "Delete", onClick = { onAction(AdminGenresAction.OnDeleteClick(genre)) }, tint = FlickQColors.Error)
                },
            )
        }
    }

    if (state.showAddDialog) {
        FlickQFormDialog(
            title = "New genre",
            confirmLabel = if (state.isSaving) "Saving…" else "Create",
            confirmEnabled = state.newName.isNotBlank() && !state.isSaving,
            onConfirm = { onAction(AdminGenresAction.OnSave) },
            onDismiss = { onAction(AdminGenresAction.OnDismissDialog) },
        ) {
            FlickQTextField(
                value = state.newName,
                onValueChange = { onAction(AdminGenresAction.OnNameChange(it)) },
                label = "Genre name",
                modifier = Modifier.fillMaxWidth(),
                isError = state.dialogError != null,
                supportingText = state.dialogError?.asString(),
            )
        }
    }

    state.deleting?.let { genre ->
        DeleteGenreDialog(genre = genre, onAction = onAction)
    }
}

@Composable
private fun DeleteGenreDialog(genre: Genre, onAction: (AdminGenresAction) -> Unit) {
    ConfirmDialog(
        title = "Delete genre?",
        message = "“${genre.name}” will be removed. Films referencing it may be affected.",
        confirmLabel = "Delete",
        onConfirm = { onAction(AdminGenresAction.OnConfirmDelete) },
        onDismiss = { onAction(AdminGenresAction.OnDismissDelete) },
    )
}
