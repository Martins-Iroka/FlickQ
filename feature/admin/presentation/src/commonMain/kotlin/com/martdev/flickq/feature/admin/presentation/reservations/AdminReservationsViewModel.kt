package com.martdev.flickq.feature.admin.presentation.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import com.martdev.flickq.reservation.model.Reservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminReservationsState(
    val isLoading: Boolean = true,
    val reservations: List<Reservation> = emptyList(),
    val error: UiText? = null,
)

sealed interface AdminReservationsAction {
    data object OnRetry : AdminReservationsAction
}

class AdminReservationsViewModel(
    private val reservations: AdminReservationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminReservationsState())
    val state = _state.asStateFlow()

    init { load() }

    fun onAction(action: AdminReservationsAction) {
        when (action) {
            AdminReservationsAction.OnRetry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            reservations.getReservations()
                .onSuccess { list -> _state.update { it.copy(isLoading = false, reservations = list) } }
                .onFailure { error, message -> _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) } }
        }
    }
}
