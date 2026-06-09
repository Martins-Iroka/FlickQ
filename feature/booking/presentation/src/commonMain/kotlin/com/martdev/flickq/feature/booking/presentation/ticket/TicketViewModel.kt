package com.martdev.flickq.feature.booking.presentation.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.booking.domain.BookingRepository
import com.martdev.flickq.feature.booking.presentation.formatNaira
import com.martdev.flickq.reservation.model.Reservation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class TicketUi(
    val movieTitle: String,
    val runtimeLabel: String,
    val seatLabel: String,
    val dateLabel: String,
    val hallTimeLabel: String,
    val code: String,
    val totalLabel: String
)

data class TicketState(
    val ticket: TicketUi? = null,
    val isLoading: Boolean = false,
    val error: UiText? = null
)

sealed interface TicketAction {
    data object OnProceedToPayment : TicketAction
    data object OnRetry : TicketAction
    data object OnBackClick : TicketAction
}

sealed interface TicketEvent {
    data class ProceedToPayment(val reservationId: Long) : TicketEvent
    data object NavigateBack : TicketEvent
}

class TicketViewModel(
    private val reservationId: Long,
    private val bookingRepository: BookingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TicketState())
    val state = _state.asStateFlow()

    private val _events = Channel<TicketEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadTicket()
    }

    fun onAction(action: TicketAction) {
        when (action) {
            TicketAction.OnProceedToPayment -> viewModelScope.launch {
                _events.send(TicketEvent.ProceedToPayment(reservationId))
            }
            TicketAction.OnRetry -> loadTicket()
            TicketAction.OnBackClick -> viewModelScope.launch {
                _events.send(TicketEvent.NavigateBack)
            }
        }
    }

    private fun loadTicket() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = bookingRepository.getReservation(reservationId)) {
                is Result.Success -> {
                    val labels = resolveSeatLabels(result.data)
                    _state.update { it.copy(isLoading = false, ticket = result.data.toTicketUi(labels)) }
                }
                is Result.Error -> _state.update {
                    it.copy(
                        isLoading = false,
                        error = resolveErrorText(result.message, result.error.toUiText()),
                    )
                }
            }
        }
    }

    private suspend fun resolveSeatLabels(reservation: Reservation): Map<Long, String> {
        val seatMap = bookingRepository.getSeatMap(reservation.showtimeId)
        return if (seatMap is Result.Success) {
            seatMap.data.seats.associate { it.seat.id to "${it.seat.rowLabel}${it.seat.seatNumber}" }
        } else {
            emptyMap()
        }
    }
}

private fun Reservation.toTicketUi(seatLabels: Map<Long, String>): TicketUi {
    val labels = seats.mapNotNull { seatLabels[it.seatId] }.sorted()
    val seatLabel = when {
        labels.isEmpty() -> "${seats.size}"
        labels.size == 1 -> labels.first()
        else -> "${labels.first()} +${labels.size - 1}"
    }
    val date = createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return TicketUi(
        movieTitle = "FlickQ Cinema",
        runtimeLabel = "RESERVATION · ${status.name}",
        seatLabel = seatLabel,
        dateLabel = date.toString(),
        hallTimeLabel = "SHOW #$showtimeId",
        code = "FQ-" + id.toString().padStart(6, '0'),
        totalLabel = formatNaira(totalAmount)
    )
}
