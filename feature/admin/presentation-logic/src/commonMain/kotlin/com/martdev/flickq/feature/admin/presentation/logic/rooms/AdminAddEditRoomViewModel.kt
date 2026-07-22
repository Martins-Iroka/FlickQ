package com.martdev.flickq.feature.admin.presentation.logic.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.room.model.Room
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TheRoomForm(
    val editingId: Long = 0,
    val name: String = "",
    val rows: String = "",
    val columns: String = "",
) {
    val isValid: Boolean
        get() = name.isNotBlank() && (rows.toIntOrNull() ?: 0) > 0 && (columns.toIntOrNull()
            ?: 0) > 0
}

data class AdminAddEditRoomState(
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val form: TheRoomForm = TheRoomForm(),
    val isSaving: Boolean = false,
    val dialogError: UiText? = null,
)

sealed interface AdminAddEditAction {
    data object OnDismiss : AdminAddEditAction
    data class OnNameChange(val value: String) : AdminAddEditAction
    data class OnRowsChange(val value: String) : AdminAddEditAction
    data class OnColumnsChange(val value: String) : AdminAddEditAction
    data object OnSave : AdminAddEditAction
}

sealed interface AdminAddEditEvent {
    data object NavigateToList : AdminAddEditEvent
}

class AdminAddEditRoomViewModel(
    private val catalog: AdminCatalogRepository,
    id: Long = 0,
    name: String = "",
    rows: String = "",
    columns: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAddEditRoomState(
        form = TheRoomForm(editingId = id, name, rows, columns)
    ))
    val state = _state.asStateFlow()

    private val _events = Channel<AdminAddEditEvent>()
    val event = _events.receiveAsFlow()

    fun onAction(action: AdminAddEditAction) {
        when (action) {
            is AdminAddEditAction.OnColumnsChange -> updateForm { it.copy(columns = action.value.filter { c -> c.isDigit() }) }
            AdminAddEditAction.OnDismiss -> {
                viewModelScope.launch {
                    _events.send(AdminAddEditEvent.NavigateToList)
                }
            }
            is AdminAddEditAction.OnNameChange -> updateForm { it.copy(name = action.value) }
            is AdminAddEditAction.OnRowsChange -> updateForm { it.copy(rows = action.value.filter { c -> c.isDigit() }) }
            AdminAddEditAction.OnSave -> save()
        }
    }

    private inline fun updateForm(transform: (TheRoomForm) -> TheRoomForm) {
        _state.update { it.copy(form = it.form.run(transform), dialogError = null) }
    }

    private fun save() {
        val form = _state.value.form
        if (!form.isValid || _state.value.isSaving) return
        val room = Room(
            id = form.editingId,
            name = form.name.trim(),
            rows = form.rows.toIntOrNull() ?: 0,
            columns = form.columns.toIntOrNull() ?: 0,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, dialogError = null) }
            val result =
                if (form.editingId == 0L) catalog.createRoom(room) else catalog.updateRoom(room)
            result
                .onSuccess {
                    _events.send(AdminAddEditEvent.NavigateToList)
                }
                .onFailure { error, message ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            dialogError = resolveErrorText(message, error.toUiText())
                        )
                    }
                }
        }
    }
}