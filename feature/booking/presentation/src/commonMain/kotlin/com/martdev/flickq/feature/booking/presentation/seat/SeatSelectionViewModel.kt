package com.martdev.flickq.feature.booking.presentation.seat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.designsystem.SeatLayout
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.booking.domain.BookingRepository
import com.martdev.flickq.feature.booking.domain.SeatMap
import com.martdev.flickq.reservation.model.SeatStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeatSelectionState(
    val rows: Int = 0,
    val columns: Int = 0,
    val seats: List<SeatLayout> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val seatPrice: Int = 0,
    val isLoading: Boolean = false,
    val isReserving: Boolean = false,
    val error: UiText? = null
) {
    val selectedCount: Int get() = selectedIds.size
    val totalAmount: Long get() = selectedIds.size.toLong() * seatPrice
    val canReserve: Boolean get() = selectedIds.isNotEmpty() && !isReserving && !isLoading
}

sealed interface SeatSelectionAction {
    data class OnSeatClick(val seatId: Long) : SeatSelectionAction
    data object OnReserveClick : SeatSelectionAction
    data object OnRetry : SeatSelectionAction
    data object OnBackClick : SeatSelectionAction
}

sealed interface SeatSelectionEvent {
    data class ReservationCreated(val reservationId: Long) : SeatSelectionEvent
    data object NavigateBack : SeatSelectionEvent
}

class SeatSelectionViewModel(
    private val showtimeId: Long,
    private val bookingRepository: BookingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SeatSelectionState())
    val state = _state.asStateFlow()

    private val _events = Channel<SeatSelectionEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadSeatMap()
    }

    fun onAction(action: SeatSelectionAction) {
        when (action) {
            is SeatSelectionAction.OnSeatClick -> toggleSeat(action.seatId)
            SeatSelectionAction.OnReserveClick -> reserve()
            SeatSelectionAction.OnRetry -> loadSeatMap()
            SeatSelectionAction.OnBackClick -> viewModelScope.launch {
                _events.send(SeatSelectionEvent.NavigateBack)
            }
        }
    }

    private fun toggleSeat(seatId: Long) {
        val seat = _state.value.seats.firstOrNull { it.id == seatId } ?: return
        if (seat.occupied) return
        _state.update {
            val selected = it.selectedIds.toMutableSet()
            if (!selected.add(seatId)) selected.remove(seatId)
            it.copy(selectedIds = selected)
        }
    }

    private fun reserve() {
        val current = _state.value
        if (current.selectedIds.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isReserving = true, error = null) }
            bookingRepository.createReservation(showtimeId, current.selectedIds.toList())
                .onSuccess { reservation ->
                    _state.update { it.copy(isReserving = false) }
                    _events.send(SeatSelectionEvent.ReservationCreated(reservation.id))
                }
                .onFailure { error, message ->
                    _state.update { it.copy(isReserving = false, error = resolveErrorText(message, error.toUiText())) }
                    // Someone may have taken a seat — refresh the map, keeping the error visible.
                    loadSeatMap(clearError = false)
                }
        }
    }

    private fun loadSeatMap(clearError: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = if (clearError) null else it.error) }
            bookingRepository.getSeatMap(showtimeId)
                .onSuccess { seatMap ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            rows = seatMap.rows,
                            columns = seatMap.columns,
                            seatPrice = seatMap.seatPrice,
                            seats = seatMap.toSeatLayouts(),
                            selectedIds = it.selectedIds.filter { id ->
                                seatMap.seats.any { s -> s.seat.id == id && s.status == SeatStatus.AVAILABLE }
                            }.toSet()
                        )
                    }
                }
                .onFailure { error, message ->
                    _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) }
                }
        }
    }
}

private fun SeatMap.toSeatLayouts(): List<SeatLayout> = seats.map { availability ->
    val seat = availability.seat
    SeatLayout(
        id = seat.id,
        rowIndex = seat.rowLabel.first() - 'A',
        colIndex = seat.seatNumber - 1,
        rowLabel = seat.rowLabel,
        seatNumber = seat.seatNumber,
        occupied = availability.status != SeatStatus.AVAILABLE
    )
}
