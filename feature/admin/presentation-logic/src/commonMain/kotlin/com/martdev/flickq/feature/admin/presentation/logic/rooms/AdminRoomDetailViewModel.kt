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

/**
 * A single room opened in the detail view, with its seat inventory loaded lazily. The Kobweb
 * admin renders this as the "Room Detail" page (stats cards, seat-map grid, inventory table);
 * the legacy Compose admin doesn't use it.
 */
data class TheRoomDetail(
    val room: Room? = null,
    val seats: List<Seat> = emptyList(),
    val isLoadingSeats: Boolean = true,
    val seatsError: UiText? = null,
) {
    /** Derived room status: seats exist once they've been generated. */
    val hasSeats: Boolean get() = seats.isNotEmpty()
}

data class RoomDetailState(
    val isLoading: Boolean = true,
    val message: UiText? = null,
    val detail: TheRoomDetail = TheRoomDetail(),
    val seatingFor: Room? = null,
)

sealed interface RoomDetailAction {
    data class OnEditClick(val room: Room) : RoomDetailAction
    data class OnGenerateSeatsClick(val room: Room) : RoomDetailAction
    data object OnRetrySeats : RoomDetailAction
    data object OnDismissGenerateSeats : RoomDetailAction
    data object OnConfirmGenerateSeats : RoomDetailAction
}

sealed interface RoomDetailEvent {
    data class NavigateToEditRoom(
        val roomData: RoomData
    ) : RoomDetailEvent
}

class AdminRoomDetailViewModel(
    roomData: Room,
    private val catalog: AdminCatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        RoomDetailState(
            detail = TheRoomDetail(room = roomData)
        )
    )
    val state = _state.asStateFlow()

    private val _event = Channel<RoomDetailEvent>()
    val event = _event.receiveAsFlow()

    init {
        loadSeats(roomData.id)
    }

    fun onAction(action: RoomDetailAction) {
        when (action) {
            RoomDetailAction.OnConfirmGenerateSeats -> generateSeats()
            RoomDetailAction.OnDismissGenerateSeats -> _state.update { it.copy(seatingFor = null) }
            is RoomDetailAction.OnEditClick -> {
                viewModelScope.launch {
                    val room = action.room
                    _event.send(RoomDetailEvent.NavigateToEditRoom(
                        RoomData(
                            room.id,
                            room.name,
                            room.rows,
                            room.columns
                        )
                    ))
                }
            }
            is RoomDetailAction.OnGenerateSeatsClick -> _state.update { it.copy(seatingFor = action.room) }
            RoomDetailAction.OnRetrySeats -> _state.value.detail.let { loadSeats(it.room!!.id) }
        }
    }

    private fun loadSeats(roomId: Long) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    detail = it.detail.copy(
                        isLoadingSeats = true,
                        seatsError = null
                    )
                )
            }
            catalog.getSeats(roomId)
                .onSuccess { seats ->
                    _state.update {
                        it.copy(
                            detail = it.detail.copy(
                                isLoadingSeats = false,
                                seats = seats
                            )
                        )
                    }
                }
                .onFailure { error, message ->
                    _state.update {
                        it.copy(
                            detail = it.detail.copy(
                                isLoadingSeats = false,
                                seatsError = resolveErrorText(message, error.toUiText())
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
            _state.update {
                it.copy(
                    seatingFor = null,
                    detail = it.detail.copy(isLoadingSeats = true)
                )
            }
            catalog.createSeats(seats)
                .onSuccess { createSeats ->
                    _state.update {
                        it.copy(
                            message = UiText.DynamicString("Created ${createSeats.size} seats for ${room.name}."),
                            detail = it.detail.copy(seats = createSeats, isLoadingSeats = false)
                        )
                    }
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