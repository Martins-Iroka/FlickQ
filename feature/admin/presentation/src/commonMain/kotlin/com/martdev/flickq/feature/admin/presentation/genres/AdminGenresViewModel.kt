package com.martdev.flickq.feature.admin.presentation.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.movie.model.Genre
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminGenresState(
    val isLoading: Boolean = true,
    val genres: List<Genre> = emptyList(),
    val error: UiText? = null,
    val showAddDialog: Boolean = false,
    val newName: String = "",
    val isSaving: Boolean = false,
    val dialogError: UiText? = null,
    val deleting: Genre? = null,
)

sealed interface AdminGenresAction {
    data object OnRetry : AdminGenresAction
    data object OnAddClick : AdminGenresAction
    data class OnNameChange(val name: String) : AdminGenresAction
    data object OnSave : AdminGenresAction
    data object OnDismissDialog : AdminGenresAction
    data class OnDeleteClick(val genre: Genre) : AdminGenresAction
    data object OnConfirmDelete : AdminGenresAction
    data object OnDismissDelete : AdminGenresAction
}

class AdminGenresViewModel(
    private val catalog: AdminCatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminGenresState())
    val state = _state.asStateFlow()

    init { load() }

    fun onAction(action: AdminGenresAction) {
        when (action) {
            AdminGenresAction.OnRetry -> load()
            AdminGenresAction.OnAddClick -> _state.update { it.copy(showAddDialog = true, newName = "", dialogError = null) }
            is AdminGenresAction.OnNameChange -> _state.update { it.copy(newName = action.name, dialogError = null) }
            AdminGenresAction.OnSave -> save()
            AdminGenresAction.OnDismissDialog -> _state.update { it.copy(showAddDialog = false) }
            is AdminGenresAction.OnDeleteClick -> _state.update { it.copy(deleting = action.genre) }
            AdminGenresAction.OnConfirmDelete -> delete()
            AdminGenresAction.OnDismissDelete -> _state.update { it.copy(deleting = null) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            catalog.getGenres()
                .onSuccess { genres -> _state.update { it.copy(isLoading = false, genres = genres) } }
                .onFailure { error, message -> _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private fun save() {
        val name = _state.value.newName.trim()
        if (name.isBlank() || _state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, dialogError = null) }
            catalog.createGenre(Genre(name = name))
                .onSuccess {
                    _state.update { it.copy(isSaving = false, showAddDialog = false) }
                    load()
                }
                .onFailure { error, message -> _state.update { it.copy(isSaving = false, dialogError = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private fun delete() {
        val target = _state.value.deleting ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = null) }
            catalog.deleteGenre(target.id)
                .onSuccess { load() }
                .onFailure { error, message -> _state.update { it.copy(error = resolveErrorText(message, error.toUiText())) } }
        }
    }
}
