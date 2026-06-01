package com.martdev.flickq.feature.admin.presentation.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminPaymentRepository
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
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
) {
    val canCancel: Boolean get() = reservation?.status == ReservationStatus.PENDING || reservation?.status == ReservationStatus.CONFIRMED
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
            _state.update { it.copy(isLoading = true, error = null) }
            reservations.getReservation(reservationId)
                .onSuccess { reservation ->
                    _state.update { it.copy(isLoading = false, reservation = reservation) }
                    payments.getPaymentsByReservation(reservationId)
                        .onSuccess { list -> _state.update { it.copy(payments = list) } }
                        // A payment lookup failure shouldn't blank the reservation; surface it inline.
                        .onFailure { error -> _state.update { it.copy(message = error.toUiText()) } }
                }
                .onFailure { error -> _state.update { it.copy(isLoading = false, error = error.toUiText()) } }
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
                .onFailure { error -> _state.update { it.copy(isCancelling = false, message = error.toUiText()) } }
        }
    }
}
