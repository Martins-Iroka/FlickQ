package com.martdev.flickq.feature.admin.presentation.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomForm(
    val editingId: Long? = null,
    val name: String = "",
    val rows: String = "",
    val columns: String = "",
) {
    val isValid: Boolean get() = name.isNotBlank() && (rows.toIntOrNull() ?: 0) > 0 && (columns.toIntOrNull() ?: 0) > 0
}

data class AdminRoomsState(
    val isLoading: Boolean = true,
    val rooms: List<Room> = emptyList(),
    val error: UiText? = null,
    val form: RoomForm? = null,
    val isSaving: Boolean = false,
    val dialogError: UiText? = null,
    val deleting: Room? = null,
    val seatingFor: Room? = null,
    val message: UiText? = null,
)

sealed interface AdminRoomsAction {
    data object OnRetry : AdminRoomsAction
    data object OnAddClick : AdminRoomsAction
    data class OnEditClick(val room: Room) : AdminRoomsAction
    data class OnNameChange(val value: String) : AdminRoomsAction
    data class OnRowsChange(val value: String) : AdminRoomsAction
    data class OnColumnsChange(val value: String) : AdminRoomsAction
    data object OnSave : AdminRoomsAction
    data object OnDismissDialog : AdminRoomsAction
    data class OnDeleteClick(val room: Room) : AdminRoomsAction
    data object OnConfirmDelete : AdminRoomsAction
    data object OnDismissDelete : AdminRoomsAction
    data class OnGenerateSeatsClick(val room: Room) : AdminRoomsAction
    data object OnConfirmGenerateSeats : AdminRoomsAction
    data object OnDismissGenerateSeats : AdminRoomsAction
    data object OnDismissMessage : AdminRoomsAction
}

class AdminRoomsViewModel(
    private val catalog: AdminCatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminRoomsState())
    val state = _state.asStateFlow()

    init { load() }

    fun onAction(action: AdminRoomsAction) {
        when (action) {
            AdminRoomsAction.OnRetry -> load()
            AdminRoomsAction.OnAddClick -> _state.update { it.copy(form = RoomForm(), dialogError = null) }
            is AdminRoomsAction.OnEditClick -> _state.update {
                it.copy(form = RoomForm(action.room.id, action.room.name, action.room.rows.toString(), action.room.columns.toString()), dialogError = null)
            }
            is AdminRoomsAction.OnNameChange -> updateForm { it.copy(name = action.value) }
            is AdminRoomsAction.OnRowsChange -> updateForm { it.copy(rows = action.value.filter { c -> c.isDigit() }) }
            is AdminRoomsAction.OnColumnsChange -> updateForm { it.copy(columns = action.value.filter { c -> c.isDigit() }) }
            AdminRoomsAction.OnSave -> save()
            AdminRoomsAction.OnDismissDialog -> _state.update { it.copy(form = null, dialogError = null) }
            is AdminRoomsAction.OnDeleteClick -> _state.update { it.copy(deleting = action.room) }
            AdminRoomsAction.OnConfirmDelete -> delete()
            AdminRoomsAction.OnDismissDelete -> _state.update { it.copy(deleting = null) }
            is AdminRoomsAction.OnGenerateSeatsClick -> _state.update { it.copy(seatingFor = action.room) }
            AdminRoomsAction.OnConfirmGenerateSeats -> generateSeats()
            AdminRoomsAction.OnDismissGenerateSeats -> _state.update { it.copy(seatingFor = null) }
            AdminRoomsAction.OnDismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private inline fun updateForm(transform: (RoomForm) -> RoomForm) {
        _state.update { it.copy(form = it.form?.let(transform), dialogError = null) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            catalog.getRooms()
                .onSuccess { rooms -> _state.update { it.copy(isLoading = false, rooms = rooms) } }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.toUiText()) } }
        }
    }

    private fun save() {
        val form = _state.value.form ?: return
        if (!form.isValid || _state.value.isSaving) return
        val room = Room(
            id = form.editingId ?: 0L,
            name = form.name.trim(),
            rows = form.rows.toIntOrNull() ?: 0,
            columns = form.columns.toIntOrNull() ?: 0,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, dialogError = null) }
            val result = if (form.editingId == null) catalog.createRoom(room) else catalog.updateRoom(room)
            result
                .onSuccess {
                    _state.update { it.copy(isSaving = false, form = null) }
                    load()
                }
                .onFailure { error -> _state.update { it.copy(isSaving = false, dialogError = error.toUiText()) } }
        }
    }

    private fun delete() {
        val target = _state.value.deleting ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = null) }
            catalog.deleteRoom(target.id)
                .onSuccess { load() }
                .onFailure { error -> _state.update { it.copy(error = error.toUiText()) } }
        }
    }

    private fun generateSeats() {
        val room = _state.value.seatingFor ?: return
        val seats = buildList {
            repeat(room.rows) { rowIndex ->
                val label = rowLabel(rowIndex)
                repeat(room.columns) { columnIndex ->
                    add(Seat(roomId = room.id, rowLabel = label, seatNumber = columnIndex + 1))
                }
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(seatingFor = null) }
            catalog.createSeats(seats)
                .onSuccess { created ->
                    _state.update { it.copy(message = UiText.DynamicString("Created ${created.size} seats for ${room.name}.")) }
                }
                .onFailure { error -> _state.update { it.copy(message = error.toUiText()) } }
        }
    }

    /** A, B, … Z, AA, AB, … for row indices beyond the alphabet. */
    private fun rowLabel(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append('A' + (n % 26))
            n = n / 26 - 1
        }
        return sb.reverse().toString()
    }
}
