package com.martdev.flickq.feature.showtime.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.showtime.domain.ShowtimeRepository
import com.martdev.flickq.feature.showtime.presentation.ShowtimeUi
import com.martdev.flickq.feature.showtime.presentation.toShowtimeUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShowtimeListState(
    val showtimes: List<ShowtimeUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiText? = null
)

sealed interface ShowtimeListAction {
    data class OnShowtimeClick(val showtimeId: Long) : ShowtimeListAction
    data object OnRetry : ShowtimeListAction
    data object OnBackClick : ShowtimeListAction
}

sealed interface ShowtimeListEvent {
    data class PickShowtime(val showtimeId: Long) : ShowtimeListEvent
    data object NavigateBack : ShowtimeListEvent
}

class ShowtimeListViewModel(
    private val movieId: Long,
    private val showtimeRepository: ShowtimeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ShowtimeListState())
    val state = _state.asStateFlow()

    private val _events = Channel<ShowtimeListEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadShowtimes()
    }

    fun onAction(action: ShowtimeListAction) {
        when (action) {
            is ShowtimeListAction.OnShowtimeClick -> onShowtimeClick(action.showtimeId)
            ShowtimeListAction.OnRetry -> loadShowtimes()
            ShowtimeListAction.OnBackClick -> viewModelScope.launch {
                _events.send(ShowtimeListEvent.NavigateBack)
            }
        }
    }

    private fun onShowtimeClick(showtimeId: Long) {
        val showtime = _state.value.showtimes.firstOrNull { it.id == showtimeId } ?: return
        if (!showtime.selectable) return
        viewModelScope.launch {
            _events.send(ShowtimeListEvent.PickShowtime(showtimeId))
        }
    }

    private fun loadShowtimes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            showtimeRepository.getShowtimesByMovieId(movieId)
                .onSuccess { showtimes ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            showtimes = showtimes.map { showtime -> showtime.toShowtimeUi() }
                        )
                    }
                }
                .onFailure { error, message ->
                    _state.update { it.copy(isLoading = false, error = resolveErrorText(message, error.toUiText())) }
                }
        }
    }
}
