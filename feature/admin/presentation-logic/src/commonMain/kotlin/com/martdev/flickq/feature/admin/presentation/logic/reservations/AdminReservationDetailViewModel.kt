package com.martdev.flickq.feature.admin.presentation.logic.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.feature.admin.domain.AdminPaymentRepository
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.showtime.model.Showtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminReservationDetailState(
    val isLoading: Boolean = true,
    val reservation: Reservation? = null,
    val payments: List<Payment> = emptyList(),
    val error: UiText? = null,
    val showCancelConfirm: Boolean = false,
    val isCancelling: Boolean = false,
    val message: UiText? = null,
    // Best-effort context joins (showtime → movie / room / seat layout); the reservation
    // still renders with raw ids if any of these lookups fail.
    val showtime: Showtime? = null,
    val movie: Movie? = null,
    val room: Room? = null,
    val roomSeats: List<Seat> = emptyList(),
) {
    val canCancel: Boolean get() = reservation?.status == ReservationStatus.PENDING || reservation?.status == ReservationStatus.CONFIRMED
    fun seat(seatId: Long): Seat? = roomSeats.firstOrNull { it.id == seatId }
}

sealed interface AdminReservationDetailAction {
    data object OnRetry : AdminReservationDetailAction
    data object OnCancelClick : AdminReservationDetailAction
    data object OnConfirmCancel : AdminReservationDetailAction
    data object OnDismissCancel : AdminReservationDetailAction
}

class AdminReservationDetailViewModel(
    private val reservationId: Long,
    private val reservations: AdminReservationRepository,
    private val payments: AdminPaymentRepository,
    private val catalog: AdminCatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminReservationDetailState())
    val state = _state.asStateFlow()

    init { load() }

    fun onAction(action: AdminReservationDetailAction) {
        when (action) {
            AdminReservationDetailAction.OnRetry -> load()
            AdminReservationDetailAction.OnCancelClick -> _state.update { it.copy(showCancelConfirm = true) }
            AdminReservationDetailAction.OnConfirmCancel -> cancel()
            AdminReservationDetailAction.OnDismissCancel -> _state.update { it.copy(showCancelConfirm = false) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            if (reservationId == 0L) {
                _state.update {
                    it.copy(isLoading = false, error = UiText.DynamicString("Invalid id"))
                }
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null) }
            reservations.getReservation(reservationId)
                .onSuccess { reservation ->
                    _state.update { it.copy(isLoading = false, reservation = reservation) }
                    loadContext(reservation.showtimeId)
                    payments.getPaymentsByReservation(reservationId)
                        .onSuccess { list -> _state.update { it.copy(payments = list) } }
                        // A payment lookup failure shouldn't blank the reservation; surface it inline.
                        .onFailure { error, message -> _state.update { it.copy(message = resolveErrorText(message, error.toUiText())) } }
                }
                .onFailure { error, message -> _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) } }
        }
    }

    /** Resolves showtime → movie/room/seat-layout context. There is no single-showtime read
     *  endpoint, so the showtime is found in the (paged) list. All lookups are best-effort. */
    private fun loadContext(showtimeId: Long) {
        viewModelScope.launch {
            catalog.getShowtimes(limit = 200, offset = 0).onSuccess { list ->
                val showtime = list.firstOrNull { it.id == showtimeId } ?: return@onSuccess
                _state.update { it.copy(showtime = showtime) }
                catalog.getMovie(showtime.movieId).onSuccess { movie -> _state.update { it.copy(movie = movie) } }
                catalog.getRooms().onSuccess { rooms -> _state.update { st -> st.copy(room = rooms.firstOrNull { it.id == showtime.roomId }) } }
                catalog.getSeats(showtime.roomId).onSuccess { seats -> _state.update { it.copy(roomSeats = seats) } }
            }
        }
    }

    private fun cancel() {
        if (_state.value.isCancelling) return
        viewModelScope.launch {
            _state.update { it.copy(isCancelling = true, showCancelConfirm = false) }
            reservations.cancelReservation(reservationId)
                .onSuccess { reservation ->
                    _state.update { it.copy(isCancelling = false, reservation = reservation, message = UiText.DynamicString("Reservation cancelled.")) }
                }
                .onFailure { error, message -> _state.update { it.copy(isCancelling = false, message = resolveErrorText(message, error.toUiText())) } }
        }
    }
}
