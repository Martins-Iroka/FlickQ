package com.martdev.flickq.feature.admin.presentation.logic.showtimes

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
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class ShowtimeForm(
    val editingId: Long? = null,
    val movieId: String = "",
    val roomId: String = "",
    val startsAt: String = "",
    val endsAt: String = "",
    val price: String = "",
    val status: ShowtimeStatus = ShowtimeStatus.SCHEDULED,
    /** True once the end time was entered/edited by hand, so auto-suggest stops overwriting it. */
    val endEdited: Boolean = false,
) {
    val isValid: Boolean
        get() = (movieId.toLongOrNull() ?: 0) > 0 && (roomId.toLongOrNull() ?: 0) > 0 &&
            startsAt.isInstant() && endsAt.isInstant() && (price.toIntOrNull() ?: -1) >= 0
}

private fun String.isInstant(): Boolean = runCatching { Instant.parse(this) }.isSuccess

data class AdminShowtimesState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val showtimes: List<Showtime> = emptyList(),
    val error: UiText? = null,
    val form: ShowtimeForm? = null,
    val isSaving: Boolean = false,
    val dialogError: UiText? = null,
    val deleting: Showtime? = null,
    val statusFor: Showtime? = null,
    val populatingFor: Showtime? = null,
    val message: UiText? = null,
    // Reference data for pickers and list joins (movie title/poster, room name).
    val movies: List<Movie> = emptyList(),
    val rooms: List<Room> = emptyList(),
    /** Full record of the movie selected in the open form (carries duration for runtime/auto-end). */
    val selectedMovie: Movie? = null,
) {
    val canLoadMore: Boolean get() = !isLoading && !isLoadingMore && !endReached && error == null
    fun movie(id: Long): Movie? = movies.firstOrNull { it.id == id }
    fun room(id: Long): Room? = rooms.firstOrNull { it.id == id }
}

sealed interface AdminShowtimesAction {
    data object OnRetry : AdminShowtimesAction
    data object OnLoadMore : AdminShowtimesAction
    data object OnAddClick : AdminShowtimesAction
    data class OnEditClick(val showtime: Showtime) : AdminShowtimesAction
    data class OnMovieIdChange(val value: String) : AdminShowtimesAction
    data class OnRoomIdChange(val value: String) : AdminShowtimesAction
    data class OnStartsAtChange(val value: String) : AdminShowtimesAction
    data class OnEndsAtChange(val value: String) : AdminShowtimesAction
    data class OnPriceChange(val value: String) : AdminShowtimesAction
    data object OnSave : AdminShowtimesAction
    data object OnDismissDialog : AdminShowtimesAction
    data class OnDeleteClick(val showtime: Showtime) : AdminShowtimesAction
    data object OnConfirmDelete : AdminShowtimesAction
    data object OnDismissDelete : AdminShowtimesAction
    data class OnStatusClick(val showtime: Showtime) : AdminShowtimesAction
    data class OnStatusPicked(val status: ShowtimeStatus) : AdminShowtimesAction
    data object OnDismissStatus : AdminShowtimesAction
    data class OnPopulateClick(val showtime: Showtime) : AdminShowtimesAction
    data object OnConfirmPopulate : AdminShowtimesAction
    data object OnDismissPopulate : AdminShowtimesAction
    data object OnDismissMessage : AdminShowtimesAction

    // --- Movie picker (Kobweb) --------------------------------------------------------
    /** Pick a movie from the search results; fetches its full record for runtime + auto-end. */
    data class OnMoviePicked(val movieId: Long) : AdminShowtimesAction
    data object OnClearMovie : AdminShowtimesAction
}

class AdminShowtimesViewModel(
    private val catalog: AdminCatalogRepository,
    private val reservations: AdminReservationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminShowtimesState())
    val state = _state.asStateFlow()

    init {
        loadRefs()
        load()
    }

    fun onAction(action: AdminShowtimesAction) {
        when (action) {
            AdminShowtimesAction.OnRetry -> { loadRefs(); load() }
            AdminShowtimesAction.OnLoadMore -> loadMore()
            AdminShowtimesAction.OnAddClick -> _state.update { it.copy(form = ShowtimeForm(), dialogError = null, selectedMovie = null) }
            is AdminShowtimesAction.OnEditClick -> {
                val s = action.showtime
                _state.update {
                    it.copy(
                        form = ShowtimeForm(
                            editingId = s.id,
                            movieId = s.movieId.toString(),
                            roomId = s.roomId.toString(),
                            startsAt = s.startsAt.toString(),
                            endsAt = s.endsAt.toString(),
                            price = s.price.toString(),
                            status = s.status,
                            endEdited = true, // keep the stored end; don't auto-overwrite on edit
                        ),
                        dialogError = null,
                        selectedMovie = null,
                    )
                }
                if (s.movieId > 0) fetchSelectedMovie(s.movieId)
            }
            is AdminShowtimesAction.OnMovieIdChange -> updateForm { it.copy(movieId = action.value.filter { c -> c.isDigit() }) }
            is AdminShowtimesAction.OnRoomIdChange -> updateForm { it.copy(roomId = action.value.filter { c -> c.isDigit() }) }
            is AdminShowtimesAction.OnStartsAtChange -> {
                updateForm { it.copy(startsAt = action.value) }
                reSuggestEnd()
            }
            is AdminShowtimesAction.OnEndsAtChange -> updateForm { it.copy(endsAt = action.value, endEdited = true) }
            is AdminShowtimesAction.OnPriceChange -> updateForm { it.copy(price = action.value.filter { c -> c.isDigit() }) }
            AdminShowtimesAction.OnSave -> save()
            AdminShowtimesAction.OnDismissDialog -> _state.update { it.copy(form = null, dialogError = null, selectedMovie = null) }
            is AdminShowtimesAction.OnDeleteClick -> _state.update { it.copy(deleting = action.showtime) }
            AdminShowtimesAction.OnConfirmDelete -> delete()
            AdminShowtimesAction.OnDismissDelete -> _state.update { it.copy(deleting = null) }
            is AdminShowtimesAction.OnStatusClick -> _state.update { it.copy(statusFor = action.showtime) }
            is AdminShowtimesAction.OnStatusPicked -> changeStatus(action.status)
            AdminShowtimesAction.OnDismissStatus -> _state.update { it.copy(statusFor = null) }
            is AdminShowtimesAction.OnPopulateClick -> _state.update { it.copy(populatingFor = action.showtime) }
            AdminShowtimesAction.OnConfirmPopulate -> populate()
            AdminShowtimesAction.OnDismissPopulate -> _state.update { it.copy(populatingFor = null) }
            AdminShowtimesAction.OnDismissMessage -> _state.update { it.copy(message = null) }
            is AdminShowtimesAction.OnMoviePicked -> {
                updateForm { it.copy(movieId = action.movieId.toString()) }
                fetchSelectedMovie(action.movieId)
            }
            AdminShowtimesAction.OnClearMovie -> {
                updateForm { it.copy(movieId = "") }
                _state.update { it.copy(selectedMovie = null) }
            }
        }
    }

    private inline fun updateForm(transform: (ShowtimeForm) -> ShowtimeForm) {
        _state.update { it.copy(form = it.form?.let(transform), dialogError = null) }
    }

    /** Loads movie (title/poster) + room reference data for pickers and list joins. */
    private fun loadRefs() {
        viewModelScope.launch {
            catalog.getMovies(limit = 200, offset = 0).onSuccess { movies -> _state.update { it.copy(movies = movies) } }
            catalog.getRooms().onSuccess { rooms -> _state.update { it.copy(rooms = rooms) } }
        }
    }

    private fun fetchSelectedMovie(movieId: Long) {
        viewModelScope.launch {
            catalog.getMovie(movieId).onSuccess { movie ->
                _state.update { it.copy(selectedMovie = movie) }
                reSuggestEnd()
            }
        }
    }

    /** Suggest end = start + runtime + buffer, unless the user has hand-edited the end. */
    private fun reSuggestEnd() {
        val s = _state.value
        val form = s.form ?: return
        val movie = s.selectedMovie ?: return
        if (form.endEdited || movie.duration <= 0) return
        val start = runCatching { Instant.parse(form.startsAt) }.getOrNull() ?: return
        val end = start + movie.duration.minutes + END_BUFFER
        _state.update { it.copy(form = it.form?.copy(endsAt = end.toString())) }
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
        val offset = if (replace) 0 else _state.value.showtimes.size
        catalog.getShowtimes(limit = PAGE_SIZE, offset = offset)
            .onSuccess { page ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        showtimes = if (replace) page else it.showtimes + page,
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

    private fun save() {
        val form = _state.value.form ?: return
        if (!form.isValid || _state.value.isSaving) return
        val now = Clock.System.now()
        val showtime = Showtime(
            id = form.editingId ?: 0L,
            movieId = form.movieId.toLongOrNull() ?: 0L,
            roomId = form.roomId.toLongOrNull() ?: 0L,
            startsAt = runCatching { Instant.parse(form.startsAt) }.getOrDefault(now),
            endsAt = runCatching { Instant.parse(form.endsAt) }.getOrDefault(now),
            price = form.price.toIntOrNull() ?: 0,
            status = form.status,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, dialogError = null) }
            val result = if (form.editingId == null) catalog.createShowtime(showtime) else catalog.updateShowtime(showtime)
            result
                .onSuccess {
                    _state.update { it.copy(isSaving = false, form = null, selectedMovie = null) }
                    load()
                }
                .onFailure { error, message -> _state.update { it.copy(isSaving = false, dialogError = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private fun delete() {
        val target = _state.value.deleting ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = null) }
            catalog.deleteShowtime(target.id)
                .onSuccess { load() }
                .onFailure { error, message -> _state.update { it.copy(error = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private fun changeStatus(status: ShowtimeStatus) {
        val target = _state.value.statusFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(statusFor = null) }
            catalog.updateShowtimeStatus(target.id, status)
                .onSuccess { load() }
                .onFailure { error, message -> _state.update { it.copy(message = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private fun populate() {
        val target = _state.value.populatingFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(populatingFor = null) }
            reservations.populateSeats(target.id)
                .onSuccess { _state.update { it.copy(message = UiText.DynamicString("Seats populated for showtime ${target.id}.")) } }
                .onFailure { error, message -> _state.update { it.copy(message = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
        val END_BUFFER = 15.minutes
    }
}
