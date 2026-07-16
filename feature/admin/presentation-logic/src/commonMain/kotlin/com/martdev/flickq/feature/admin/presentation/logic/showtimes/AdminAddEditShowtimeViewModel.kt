package com.martdev.flickq.feature.admin.presentation.logic.showtimes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class TheShowtimeForm(
    val editingId: Long = 0,
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

data class AddEditShowtimeState(
    val isSaving: Boolean = false,
    val dialogError: UiText? = null,
    val rooms: List<Room> = emptyList(),
    /** Full record of the movie selected in the open form (carries duration for runtime/auto-end). */
    val selectedMovie: Movie? = null,
    val form: TheShowtimeForm = TheShowtimeForm(),
    val movies: List<Movie> = emptyList(),
) {
    fun movie(id: Long): Movie? = movies.firstOrNull { it.id == id }
}

sealed interface AddEditShowtimeAction {
    data object OnSave : AddEditShowtimeAction
    data object OnClearMovie : AddEditShowtimeAction
    data class OnMoviePicked(val movieId: Long) : AddEditShowtimeAction
    data class OnRoomIdChange(val value: String) : AddEditShowtimeAction
    data class OnEndsAtChange(val value: String) : AddEditShowtimeAction
    data class OnPriceChange(val value: String) : AddEditShowtimeAction
    data class OnStartsAtChange(val value: String) : AddEditShowtimeAction
}

sealed interface AddEditShowtimeEvent {
    data object NavigateToList : AddEditShowtimeEvent
}

class AdminAddEditShowtimeViewModel(
    val form: TheShowtimeForm,
    private val catalog: AdminCatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AddEditShowtimeState(
        form = form
    ))
    val state = _state.asStateFlow()

    private val _event = Channel<AddEditShowtimeEvent>()
    val event = _event.receiveAsFlow()

    init {
        loadRefs()
    }
    fun onAction(action: AddEditShowtimeAction) {
        when(action) {
            AddEditShowtimeAction.OnClearMovie -> {
                updateForm { it.copy(movieId = "") }
                _state.update { it.copy(selectedMovie = null) }
            }
            is AddEditShowtimeAction.OnEndsAtChange -> updateForm { it.copy(endsAt = action.value, endEdited = true) }
            is AddEditShowtimeAction.OnMoviePicked -> {
                updateForm { it.copy(movieId = action.movieId.toString()) }
                fetchSelectedMovie(action.movieId)
            }
            is AddEditShowtimeAction.OnPriceChange -> updateForm { it.copy(price = action.value.filter { c -> c.isDigit() }) }
            is AddEditShowtimeAction.OnRoomIdChange -> updateForm { it.copy(roomId = action.value.filter { c -> c.isDigit() }) }
            AddEditShowtimeAction.OnSave -> save()
            is AddEditShowtimeAction.OnStartsAtChange -> {
                updateForm { it.copy(startsAt = action.value) }
                reSuggestEnd()
            }
        }
    }

    private inline fun updateForm(transform: (TheShowtimeForm) -> TheShowtimeForm) {
        _state.update { it.copy(form = it.form.run(transform), dialogError = null) }
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
        val form = s.form
        val movie = s.selectedMovie ?: return
        if (form.endEdited || movie.duration <= 0) return
        val start = runCatching { Instant.parse(form.startsAt) }.getOrNull() ?: return
        val end = start + movie.duration.minutes + END_BUFFER
        _state.update { it.copy(form = it.form.copy(endsAt = end.toString())) }
    }

    private fun save() {
        val form = _state.value.form
        if (!form.isValid || _state.value.isSaving) return
        val now = Clock.System.now()
        val showtime = Showtime(
            id = form.editingId,
            movieId = form.movieId.toLongOrNull() ?: 0L,
            roomId = form.roomId.toLongOrNull() ?: 0L,
            startsAt = runCatching { Instant.parse(form.startsAt) }.getOrDefault(now),
            endsAt = runCatching { Instant.parse(form.endsAt) }.getOrDefault(now),
            price = form.price.toIntOrNull() ?: 0,
            status = form.status,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, dialogError = null) }
            val result = if (form.editingId == 0L) catalog.createShowtime(showtime) else catalog.updateShowtime(showtime)
            result
                .onSuccess {
                    _state.update { it.copy(isSaving = false, form = TheShowtimeForm(), selectedMovie = null) }
                    _event.send(AddEditShowtimeEvent.NavigateToList)
                }
                .onFailure { error, message -> _state.update { it.copy(isSaving = false, dialogError = resolveErrorText(message, error.toUiText())) } }
        }
    }

    /** Loads movie (title/poster) + room reference data for pickers and list joins. */
    private fun loadRefs() {
        viewModelScope.launch {
            catalog.getMovies(limit = 200, offset = 0).onSuccess { movies -> _state.update { it.copy(movies = movies) } }
            catalog.getRooms().onSuccess { rooms -> _state.update { it.copy(rooms = rooms) } }
        }
    }

    private companion object {
        val END_BUFFER = 15.minutes
    }
}