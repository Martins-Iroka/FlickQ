package com.martdev.flickq.feature.admin.presentation.logic.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.showtime.model.Showtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminReservationsState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val reservations: List<Reservation> = emptyList(),
    val error: UiText? = null,
    // Reference data for list joins: reservation.showtimeId → showtime → movie title / room name.
    val showtimes: List<Showtime> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val rooms: List<Room> = emptyList(),
) {
    val canLoadMore: Boolean get() = !isLoading && !isLoadingMore && !endReached && error == null
    fun showtime(id: Long): Showtime? = showtimes.firstOrNull { it.id == id }
    fun movie(id: Long): Movie? = movies.firstOrNull { it.id == id }
    fun room(id: Long): Room? = rooms.firstOrNull { it.id == id }
}

sealed interface AdminReservationsAction {
    data object OnRetry : AdminReservationsAction
    data object OnLoadMore : AdminReservationsAction
}

class AdminReservationsViewModel(
    private val reservations: AdminReservationRepository,
    private val catalog: AdminCatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminReservationsState())
    val state = _state.asStateFlow()

    init {
        loadRefs()
        load()
    }

    fun onAction(action: AdminReservationsAction) {
        when (action) {
            AdminReservationsAction.OnRetry -> { loadRefs(); load() }
            AdminReservationsAction.OnLoadMore -> loadMore()
        }
    }

    /** Best-effort joins — rows fall back to "Showtime #id" when a lookup fails. */
    private fun loadRefs() {
        viewModelScope.launch {
            catalog.getShowtimes(limit = 200, offset = 0).onSuccess { list -> _state.update { it.copy(showtimes = list) } }
            catalog.getMovies(limit = 200, offset = 0).onSuccess { list -> _state.update { it.copy(movies = list) } }
            catalog.getRooms().onSuccess { list -> _state.update { it.copy(rooms = list) } }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isLoadingMore = false, endReached = false, error = null) }
            fetchPage(replace = true)
        }
    }

    private fun loadMore() {
        if (!_state.value.canLoadMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { fetchPage(replace = false) }
    }

    /** Loads the page at the current offset; [replace] seeds the first page, otherwise appends. */
    private suspend fun fetchPage(replace: Boolean) {
        val offset = if (replace) 0 else _state.value.reservations.size
        reservations.getReservations(limit = PAGE_SIZE, offset = offset)
            .onSuccess { page ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        reservations = if (replace) page else it.reservations + page,
                        endReached = page.size < PAGE_SIZE,
                        error = null,
                    )
                }
            }
            .onFailure { error, message ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = if (replace) resolveErrorText(message, error.toUiText()) else null,
                    )
                }
            }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
