package com.martdev.flickq.feature.reservation.presentation.myreservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
import kotlin.time.Clock
import kotlin.time.Instant

data class ReservationListState(
    val reservations: List<ReservationTicketUI> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: UiText? = null,
    val showDialogBox: Boolean = false,
    val status: String = "ALL",
    val reservationTickets: PagingData<ReservationTicketUI> = PagingData.empty()
) {
    val canLoadMore: Boolean get() = !isLoading && !isLoadingMore && !endReached && error == null
}

sealed interface ReservationListAction {
    data object OnReservationClicked : ReservationListAction
    data object OnLoadMore : ReservationListAction
    data object OnRetry : ReservationListAction
    data object OnShowStatusDialog : ReservationListAction
    data class OnStatusSelected(val status: String?) : ReservationListAction
    data class OnPayForPendingReservation(val reservationId: Long, val expiry: Instant) : ReservationListAction
}

sealed interface ReservationListEvent {
    data object NavigateToDetail : ReservationListEvent
    data class NavigateToPayment(val reservationId: Long) : ReservationListEvent
}

class MyReservationViewModel(
    private val reservationRepository: MobileReservationRepository
) : ViewModel() {

    val state: StateFlow<ReservationListState>
        field = MutableStateFlow(ReservationListState())

    private val _events = Channel<ReservationListEvent>()
    val event = _events.receiveAsFlow()

    var status: ReservationStatus? = null

    val reservationList = Pager(
        PagingConfig(
            pageSize = 5
        )
    ) {
        MyReservationPagingSource(
            reservationRepository
        )
    }.flow.cachedIn(viewModelScope)

   /* init {
        loadFirstPage()
    }*/

    fun onAction(action: ReservationListAction) {
        when (action) {
            ReservationListAction.OnLoadMore -> loadMore()
            ReservationListAction.OnReservationClicked -> {
                viewModelScope.launch {
                    _events.send(ReservationListEvent.NavigateToDetail)
                }
            }
            ReservationListAction.OnRetry -> {
//                loadFirstPage()
            }
            ReservationListAction.OnShowStatusDialog -> state.update {
                it.copy(showDialogBox = true)
            }

            is ReservationListAction.OnStatusSelected -> {
                state.update {
                    it.copy(
                        status = action.status.orEmpty().ifEmpty { "ALL" },
                        showDialogBox = false
                    )
                }
                val s = runCatching {
                    ReservationStatus.valueOf(action.status.orEmpty())
                }.getOrNull()
//                loadFirstPage(s)
            }
            is ReservationListAction.OnPayForPendingReservation -> {
                val now = Clock.System.now()
                if (now >= action.expiry) {
//                    loadFirstPage(status)
                } else {
                    viewModelScope.launch {
                        _events.send(ReservationListEvent.NavigateToPayment(action.reservationId))
                    }
                }
            }
        }
    }

    private fun loadMore() {
        if (!state.value.canLoadMore) return
        state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { fetchPage(false) }
    }

    private suspend fun fetchPage(replace: Boolean, status: ReservationStatus? = null) {
        val offset = if (replace) 0 else state.value.reservations.size
        reservationRepository.getMyReservationTickets(
            status = status,
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