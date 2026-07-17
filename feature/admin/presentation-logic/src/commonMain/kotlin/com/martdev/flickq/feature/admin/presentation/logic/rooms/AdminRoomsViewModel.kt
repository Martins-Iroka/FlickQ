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
import com.martdev.flickq.room.model.Seat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomForm(
    val editingId: Long? = null,
    val name: String = "",
    val rows: String = "",
    val columns: String = "",
) {
    val isValid: Boolean
        get() = name.isNotBlank() && (rows.toIntOrNull() ?: 0) > 0 && (columns.toIntOrNull()
            ?: 0) > 0
}

/**
 * A single room opened in the detail view, with its seat inventory loaded lazily. The Kobweb
 * admin renders this as the "Room Detail" page (stats cards, seat-map grid, inventory table);
 * the legacy Compose admin doesn't use it.
 */
data class RoomDetail(
    val room: Room,
    val seats: List<Seat> = emptyList(),
    val isLoadingSeats: Boolean = true,
    val seatsError: UiText? = null,
) {
    /** Derived room status: seats exist once they've been generated. */
    val hasSeats: Boolean get() = seats.isNotEmpty()
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
    val detail: RoomDetail? = null,
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

    // --- Detail view (Kobweb) ---------------------------------------------------------
    data class OnRoomClick(val room: Room) : AdminRoomsAction
    data object OnCloseDetail : AdminRoomsAction
    data object OnRetrySeats : AdminRoomsAction
}

sealed interface AdminRoomsEvent {
    data object NavigateToAddNewRoom : AdminRoomsEvent
    data class NavigateToEditRoom(
        val id: Long,
        val name: String,
        val rows: String,
        val columns: String
    ) : AdminRoomsEvent

    data class NavigateToRoomDetail(val roomData: RoomData) : AdminRoomsEvent
}

class AdminRoomsViewModel(
    private val catalog: AdminCatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminRoomsState())
    val state = _state.asStateFlow()

    private val _event = Channel<AdminRoomsEvent>()
    val event = _event.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: AdminRoomsAction) {
        when (action) {
            AdminRoomsAction.OnRetry -> load()
            AdminRoomsAction.OnAddClick -> {
                viewModelScope.launch {
                    _event.send(AdminRoomsEvent.NavigateToAddNewRoom)
                }
            }

            is AdminRoomsAction.OnEditClick -> {
                viewModelScope.launch {
                    _event.send(
                        AdminRoomsEvent.NavigateToEditRoom(
                            action.room.id,
                            action.room.name,
                            action.room.rows.toString(),
                            action.room.columns.toString()
                        )
                    )
                }
            }

            is AdminRoomsAction.OnNameChange -> updateForm { it.copy(name = action.value) }
            is AdminRoomsAction.OnRowsChange -> updateForm { it.copy(rows = action.value.filter { c -> c.isDigit() }) }
            is AdminRoomsAction.OnColumnsChange -> updateForm { it.copy(columns = action.value.filter { c -> c.isDigit() }) }
            AdminRoomsAction.OnSave -> save()
            AdminRoomsAction.OnDismissDialog -> _state.update {
                it.copy(
                    form = null,
                    dialogError = null
                )
            }

            is AdminRoomsAction.OnDeleteClick -> _state.update { it.copy(deleting = action.room) }
            AdminRoomsAction.OnConfirmDelete -> delete()
            AdminRoomsAction.OnDismissDelete -> _state.update { it.copy(deleting = null) }
            is AdminRoomsAction.OnGenerateSeatsClick -> _state.update { it.copy(seatingFor = action.room) }
            AdminRoomsAction.OnConfirmGenerateSeats -> generateSeats()
            AdminRoomsAction.OnDismissGenerateSeats -> _state.update { it.copy(seatingFor = null) }
            AdminRoomsAction.OnDismissMessage -> _state.update { it.copy(message = null) }
            is AdminRoomsAction.OnRoomClick -> {
//                openDetail(action.room)
                viewModelScope.launch {
                    val room = action.room
                    _event.send(
                        AdminRoomsEvent.NavigateToRoomDetail(
                            RoomData(
                                room.id,
                                room.name,
                                room.rows,
                                room.columns
                            )
                        )
                    )
                }
            }

            AdminRoomsAction.OnCloseDetail -> _state.update { it.copy(detail = null) }
            AdminRoomsAction.OnRetrySeats -> _state.value.detail?.let { loadSeats(it.room) }
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
                .onFailure { error, message ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = resolveErrorText(message, error.toUiText())
                        )
                    }
                }
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
            val result =
                if (form.editingId == null) catalog.createRoom(room) else catalog.updateRoom(room)
            result
                .onSuccess { saved ->
                    // If the saved room is the one open in detail, reflect the edit there too.
                    _state.update { s ->
                        val detail = s.detail?.takeIf { it.room.id == saved.id }?.copy(room = saved)
                            ?: s.detail
                        s.copy(isSaving = false, form = null, detail = detail)
                    }
                    load()
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

    private fun delete() {
        val target = _state.value.deleting ?: return
        viewModelScope.launch {
            // Leaving the detail view too if we just deleted the room it was showing.
            _state.update {
                it.copy(
                    deleting = null,
                    detail = it.detail?.takeIf { d -> d.room.id != target.id })
            }
            catalog.deleteRoom(target.id)
                .onSuccess { load() }
                .onFailure { error, message ->
                    _state.update {
                        it.copy(
                            error = resolveErrorText(
                                message,
                                error.toUiText()
                            )
                        )
                    }
                }
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
                    // Refresh the inventory if this room is open in the detail view.
                    if (_state.value.detail?.room?.id == room.id) loadSeats(room)
                }
                .onFailure { error, message ->
                    _state.update {
                        it.copy(
                            message = resolveErrorText(
                                message,
                                error.toUiText()
                            )
                        )
                    }
                }
        }
    }

    private fun openDetail(room: Room) {
        _state.update { it.copy(detail = RoomDetail(room = room, isLoadingSeats = true)) }
        loadSeats(room)
    }

    private fun loadSeats(room: Room) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    detail = it.detail?.copy(
                        isLoadingSeats = true,
                        seatsError = null
                    )
                )
            }
            catalog.getSeats(room.id)
                .onSuccess { seats ->
                    _state.update {
                        it.copy(
                            detail = it.detail?.copy(
                                isLoadingSeats = false,
                                seats = seats
                            )
                        )
                    }
                }
                .onFailure { error, message ->
                    _state.update {
                        it.copy(
                            detail = it.detail?.copy(
                                isLoadingSeats = false,
                                seatsError = resolveErrorText(message, error.toUiText())
                            )
                        )
                    }
                }
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
