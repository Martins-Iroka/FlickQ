package com.martdev.flickq.feature.admin.presentation.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
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
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdminMoviesRoot(
    onBack: () -> Unit,
    viewModel: AdminMoviesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdminMoviesScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun AdminMoviesScreen(
    state: AdminMoviesState,
    onAction: (AdminMoviesAction) -> Unit,
    onBack: () -> Unit,
) {
    AdminScaffold(
        title = "Movies",
        onBack = onBack,
        actions = { RowAction(label = "+ Add", onClick = { onAction(AdminMoviesAction.OnAddClick) }) },
    ) {
        when {
            state.isLoading -> AdminLoading()
            state.error != null -> AdminError(message = state.error.asString(), onRetry = { onAction(AdminMoviesAction.OnRetry) })
            state.movies.isEmpty() -> AdminEmpty(message = "No movies yet. Tap “+ Add” to create one.", modifier = Modifier.padding(24.dp))
            else -> DataTable(
                items = state.movies,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                columns = listOf(
                    DataColumn("ID", 0.25f) { it.id.toString() },
                    DataColumn("Title", 1f) { it.title },
                ),
                rowActions = { movie ->
                    RowAction(label = "Edit", onClick = { onAction(AdminMoviesAction.OnEditClick(movie)) })
                    RowAction(label = "Delete", onClick = { onAction(AdminMoviesAction.OnDeleteClick(movie)) }, tint = FlickQColors.Error)
                },
                canLoadMore = state.canLoadMore,
                isLoadingMore = state.isLoadingMore,
                onLoadMore = { onAction(AdminMoviesAction.OnLoadMore) },
            )
        }
    }

    state.form?.let { form ->
        FlickQFormDialog(
            title = if (form.editingId == null) "New movie" else "Edit movie",
            confirmLabel = if (state.isSaving) "Saving…" else "Save",
            confirmEnabled = form.isValid && !state.isSaving,
            onConfirm = { onAction(AdminMoviesAction.OnSave) },
            onDismiss = { onAction(AdminMoviesAction.OnDismissDialog) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlickQTextField(form.title, { onAction(AdminMoviesAction.OnTitleChange(it)) }, "Title", Modifier.fillMaxWidth())
                FlickQTextField(form.description, { onAction(AdminMoviesAction.OnDescriptionChange(it)) }, "Description", Modifier.fillMaxWidth(), singleLine = false)
                FlickQTextField(form.posterUrl, { onAction(AdminMoviesAction.OnPosterUrlChange(it)) }, "Poster URL", Modifier.fillMaxWidth())
                FlickQTextField(
                    form.duration, { onAction(AdminMoviesAction.OnDurationChange(it)) }, "Duration (minutes)",
                    Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlickQTextField(form.releasedDate, { onAction(AdminMoviesAction.OnReleasedDateChange(it)) }, "Released date (YYYY-MM-DD)", Modifier.fillMaxWidth())
                if (state.genres.isNotEmpty()) {
                    Text(text = "Genres", color = FlickQColors.GoldHighlight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.genres.forEach { genre ->
                            FilterChip(
                                selected = genre.id in form.genreIds,
                                onClick = { onAction(AdminMoviesAction.OnToggleGenre(genre.id)) },
                                label = { Text(genre.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FlickQColors.Gold,
                                    selectedLabelColor = FlickQColors.OnGold,
                                ),
                            )
                        }
                    }
                }
                state.dialogError?.let { Text(text = it.asString(), color = FlickQColors.Error, fontSize = 12.sp) }
            }
        }
    }

    state.deleting?.let { movie ->
        ConfirmDialog(
            title = "Delete movie?",
            message = "“${movie.title}” will be removed. If it has showtimes the server may reject the delete.",
            confirmLabel = "Delete",
            onConfirm = { onAction(AdminMoviesAction.OnConfirmDelete) },
            onDismiss = { onAction(AdminMoviesAction.OnDismissDelete) },
        )
    }
}
