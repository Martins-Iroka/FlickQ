package com.martdev.flickq.feature.reservation.presentation.myreservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.martdev.flickq.feature.reservation.domain.MobileReservationRepository
import com.martdev.flickq.reservation.model.ReservationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

data class ReservationListState(
    val status: String = "ALL",
)

sealed interface ReservationListAction {
    data object OnReservationClicked : ReservationListAction
    data object OnRetry : ReservationListAction
    data class OnStatusSelected(val status: String?) : ReservationListAction
    data class OnPayForPendingReservation(val reservationId: Long, val expiry: Instant) : ReservationListAction
}

sealed interface ReservationListEvent {
    data object NavigateToDetail : ReservationListEvent
    data class NavigateToPayment(val reservationId: Long) : ReservationListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class MyReservationViewModel(
    private val reservationRepository: MobileReservationRepository
) : ViewModel() {

    val state: StateFlow<ReservationListState>
        field = MutableStateFlow(ReservationListState())

    private val _events = Channel<ReservationListEvent>(Channel.BUFFERED)
    val event = _events.receiveAsFlow()

    private var status: String? = null

    private val pager = Pager(
        PagingConfig(
            pageSize = PAGE_SIZE
        )
    ) {
        val s = runCatching {
            ReservationStatus.valueOf(state.value.status)
        }.getOrNull()
        MyReservationPagingSource(
            reservationRepository,
            s
        )
    }
    val reservationList = state
        .flatMapLatest { _ ->
            pager.flow
        }.cachedIn(viewModelScope)

    fun onAction(action: ReservationListAction) {
        when (action) {
            ReservationListAction.OnReservationClicked -> {
                viewModelScope.launch {
                    _events.send(ReservationListEvent.NavigateToDetail)
                }
            }
            ReservationListAction.OnRetry -> {
                pager.retry()
            }

            is ReservationListAction.OnStatusSelected -> {
                status = action.status
                state.update {
                    it.copy(
                        status = action.status.orEmpty().ifEmpty { "ALL" }
                    )
                }
            }
            is ReservationListAction.OnPayForPendingReservation -> {
                val now = Clock.System.now()
                if (now >= action.expiry) {
                    state.update {
                        it.copy(
                            status = status ?: "ALL"
                        )
                    }
                } else {
                    viewModelScope.launch {
                        _events.send(ReservationListEvent.NavigateToPayment(action.reservationId))
                    }
                }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}