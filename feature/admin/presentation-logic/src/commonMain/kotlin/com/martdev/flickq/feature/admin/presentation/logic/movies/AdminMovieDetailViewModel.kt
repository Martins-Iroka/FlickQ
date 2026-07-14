package com.martdev.flickq.feature.admin.presentation.logic.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Form fields for creating/editing a movie. [editingId] is null for a create. */
data class TheMovieForm(
    val editingId: Long? = null,
    val title: String = "",
    val description: String = "",
    val posterUrl: String = "",
    val duration: String = "",
    val releasedDate: String = "",
    val genreIds: Set<Long> = emptySet(),
) {
    val isValid: Boolean get() = title.isNotBlank() && description.isNotBlank() && posterUrl.isNotBlank() && duration.isNotBlank() && releasedDate.isNotBlank() && genreIds.isNotEmpty()
}

data class AdminMovieDetailState(
    val dialogError: UiText? = null,
    val error: UiText? = null,
    val form: TheMovieForm = TheMovieForm(),
    val genres: List<Genre> = emptyList(),
    val newGenre: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSavingGenre: Boolean = false
)

sealed interface AdminMovieAction {
    data object OnDismiss : AdminMovieAction
    data object OnSave : AdminMovieAction
    data class OnTitleChange(val value: String) : AdminMovieAction
    data class OnDescriptionChange(val value: String) : AdminMovieAction
    data class OnDurationChange(val value: String) : AdminMovieAction
    data class OnReleasedDateChange(val value: String) : AdminMovieAction
    data object OnAddGenreClick : AdminMovieAction
    data class OnToggleGenre(val genreId: Long) : AdminMovieAction
    data class OnPosterUrlChange(val value: String) : AdminMovieAction
    data class OnNewGenreChange(val value: String) : AdminMovieAction
    data object OnSubmitGenre : AdminMovieAction
    data object OnCancelGenre : AdminMovieAction
}

sealed interface AdminMovieEvent {
    data object MovieSaved : AdminMovieEvent
    data object BackToMovieList : AdminMovieEvent
}

class AdminMovieDetailViewModel(
    id: Long = 0,
    private val catalog: AdminCatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminMovieDetailState())
    val state = _state.asStateFlow()

    private val _events = Channel<AdminMovieEvent>()
    val events = _events.receiveAsFlow()

    init {
        load(id)
    }

    fun onAction(action: AdminMovieAction) {
        when (action) {
            AdminMovieAction.OnAddGenreClick -> _state.update {
                it.copy(
                    newGenre = "",
                    dialogError = null
                )
            }

            AdminMovieAction.OnCancelGenre -> _state.update { it.copy(newGenre = null) }
            is AdminMovieAction.OnDescriptionChange -> updateForm { it.copy(description = action.value) }
            AdminMovieAction.OnDismiss -> {
                viewModelScope.launch {
                    _events.send(AdminMovieEvent.BackToMovieList)
                }
            }

            is AdminMovieAction.OnDurationChange -> updateForm { it.copy(duration = action.value.filter { c -> c.isDigit() }) }
            is AdminMovieAction.OnNewGenreChange -> _state.update { it.copy(newGenre = action.value) }
            is AdminMovieAction.OnPosterUrlChange -> updateForm { it.copy(posterUrl = action.value) }
            is AdminMovieAction.OnReleasedDateChange -> updateForm { it.copy(releasedDate = action.value) }
            AdminMovieAction.OnSave -> save()
            AdminMovieAction.OnSubmitGenre -> submitGenre()
            is AdminMovieAction.OnTitleChange -> updateForm { it.copy(title = action.value) }
            is AdminMovieAction.OnToggleGenre -> updateForm { form ->
                form.copy(genreIds = if (action.genreId in form.genreIds) form.genreIds - action.genreId else form.genreIds + action.genreId)
            }
        }
    }

    private inline fun updateForm(transform: (TheMovieForm) -> TheMovieForm) {
        _state.update { it.copy(form = it.form.run(transform), dialogError = null) }
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            // Genres back the form's chips; fetched once alongside the first page.
            catalog.getGenres().onSuccess { genres -> _state.update { it.copy(genres = genres) } }
            if (id != 0L) {
                openEdit(id)
            }
        }
    }
    private suspend fun openEdit(id: Long) {
        _state.update {
            it.copy(isLoading = true)
        }
        catalog.getMovie(id)
            .onSuccess { movie ->
                _state.update {
                    it.copy(
                        form = TheMovieForm(
                            editingId = movie.id,
                            title = movie.title,
                            description = movie.description,
                            posterUrl = movie.posterUrl,
                            duration = movie.duration.toString(),
                            releasedDate = movie.releasedDate.toString(),
                            genreIds = movie.genres.map { g -> g.id }.toSet(),
                        ),
                        dialogError = null,
                        newGenre = null,
                        isLoading = false
                    )
                }
            }
            .onFailure { error, message ->
                _state.update {
                    it.copy(
                        error = resolveErrorText(
                            message,
                            error.toUiText()
                        ),
                        isLoading = false
                    )
                }
            }
    }

    private fun save() {
        val form = _state.value.form
        if (!form.isValid || _state.value.isSaving) return
        val movie = Movie(
            id = form.editingId ?: 0L,
            title = form.title.trim(),
            description = form.description.trim(),
            posterUrl = form.posterUrl.trim(),
            duration = form.duration.toIntOrNull() ?: 0,
            releasedDate = form.releasedDate.toLocalDateOrToday(),
            genres = _state.value.genres.filter { it.id in form.genreIds },
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, dialogError = null) }
            val result =
                if (form.editingId == null) catalog.createMovie(movie) else catalog.updateMovie(
                    movie
                )
            result
                .onSuccess {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(AdminMovieEvent.MovieSaved)
                }
                .onFailure { error, message ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            dialogError = resolveErrorText(message, error.toUiText())
                        )
                    }
                }
        }
    }

    /**
     * Creates a genre inline, then refetches the list to learn its server-assigned id and
     * auto-selects it on the open form. [createGenre] returns no body, so we match by name.
     */
    private fun submitGenre() {
        val name = _state.value.newGenre?.trim().orEmpty()
        if (name.isBlank() || _state.value.isSavingGenre) return
        viewModelScope.launch {
            _state.update { it.copy(isSavingGenre = true, dialogError = null) }
            catalog.createGenre(Genre(name = name))
                .onSuccess {
                    catalog.getGenres()
                        .onSuccess { genres ->
                            val created =
                                genres.firstOrNull { it.name.equals(name, ignoreCase = true) }
                            _state.update { s ->
                                s.copy(
                                    genres = genres,
                                    isSavingGenre = false,
                                    newGenre = null,
                                    form = s.form.run {
                                        if (created != null) copy(genreIds = genreIds + created.id) else this
                                    },
                                )
                            }
                        }
                        .onFailure { error, message ->
                            _state.update {
                                it.copy(
                                    isSavingGenre = false,
                                    newGenre = null,
                                    dialogError = resolveErrorText(message, error.toUiText())
                                )
                            }
                        }
                }
                .onFailure { error, message ->
                    _state.update {
                        it.copy(
                            isSavingGenre = false,
                            dialogError = resolveErrorText(message, error.toUiText())
                        )
                    }
                }
        }
    }

    private fun String.toLocalDateOrToday(): LocalDate =
        takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).date
}