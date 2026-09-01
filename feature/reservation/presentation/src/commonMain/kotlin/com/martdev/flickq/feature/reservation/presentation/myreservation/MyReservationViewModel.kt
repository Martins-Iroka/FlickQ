package com.martdev.flickq.feature.reservation.presentation.myreservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.reservation.domain.MobileReservationRepository
import com.martdev.flickq.feature.reservation.presentation.ReservationTicketUI
import com.martdev.flickq.feature.reservation.presentation.toReservationTicketUI
import com.martdev.flickq.reservation.model.ReservationStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReservationListState(
    val reservations: List<ReservationTicketUI> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: UiText? = null
) {
    val canLoadMore: Boolean get() = !isLoading && !isLoadingMore && !endReached && error == null
}

sealed interface ReservationListAction {
    data object OnReservationClicked : ReservationListAction
    data object OnLoadMore : ReservationListAction
    data object OnRetry : ReservationListAction
}

sealed interface ReservationListEvent {
    data object NavigateToDetail : ReservationListEvent
}

class MyReservationViewModel(
    private val reservationRepository: MobileReservationRepository
) : ViewModel() {

    val state: StateFlow<ReservationListState>
        field = MutableStateFlow(ReservationListState())

    private val _events = Channel<ReservationListEvent>()
    val event = _events.receiveAsFlow()

    init {
        loadFirstPage()
    }

    fun onAction(action: ReservationListAction) {
        when (action) {
            ReservationListAction.OnLoadMore -> loadMore()
            ReservationListAction.OnReservationClicked -> {
                viewModelScope.launch {
                    _events.send(ReservationListEvent.NavigateToDetail)
                }
            }
            ReservationListAction.OnRetry -> loadFirstPage()
        }
    }

    private fun loadFirstPage() {
        state.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                endReached = false
            )
        }
        viewModelScope.launch { fetchPage(true) }
    }

    private fun loadMore() {
        if (!state.value.canLoadMore) return
        state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { fetchPage(false) }
    }

    private suspend fun fetchPage(replace: Boolean) {
        val offset = if (replace) 0 else state.value.reservations.size
        reservationRepository.getMyReservationTickets(
            status = ReservationStatus.CONFIRMED,
            limit = PAGE_SIZE,
            offset = offset
        ).onSuccess { page ->
            val ui = page.map { it.toReservationTicketUI() }
            state.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    reservations = if (replace) ui else it.reservations + ui,
                    endReached = page.size < PAGE_SIZE,
                    error = null
                )
            }
        }.onFailure { error, message ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = if (replace) resolveErrorText(message, error.toUiText()) else null
                    )
                }
            }
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}