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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Form fields for creating/editing a movie. [editingId] is null for a create. */
data class MovieForm(
    val editingId: Long? = null,
    val title: String = "",
    val description: String = "",
    val posterUrl: String = "",
    val duration: String = "",
    val releasedDate: String = "",
    val genreIds: Set<Long> = emptySet(),
) {
    val isValid: Boolean get() = title.isNotBlank()
}

data class AdminMoviesState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val movies: List<Movie> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val error: UiText? = null,
    val form: MovieForm? = null,
    val isSaving: Boolean = false,
    val dialogError: UiText? = null,
    val deleting: Movie? = null,
) {
    val canLoadMore: Boolean get() = !isLoading && !isLoadingMore && !endReached && error == null
}

sealed interface AdminMoviesAction {
    data object OnRetry : AdminMoviesAction
    data object OnLoadMore : AdminMoviesAction
    data object OnAddClick : AdminMoviesAction
    data class OnEditClick(val movie: Movie) : AdminMoviesAction
    data class OnTitleChange(val value: String) : AdminMoviesAction
    data class OnDescriptionChange(val value: String) : AdminMoviesAction
    data class OnPosterUrlChange(val value: String) : AdminMoviesAction
    data class OnDurationChange(val value: String) : AdminMoviesAction
    data class OnReleasedDateChange(val value: String) : AdminMoviesAction
    data class OnToggleGenre(val genreId: Long) : AdminMoviesAction
    data object OnSave : AdminMoviesAction
    data object OnDismissDialog : AdminMoviesAction
    data class OnDeleteClick(val movie: Movie) : AdminMoviesAction
    data object OnConfirmDelete : AdminMoviesAction
    data object OnDismissDelete : AdminMoviesAction
}

class AdminMoviesViewModel(
    private val catalog: AdminCatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminMoviesState())
    val state = _state.asStateFlow()

    init { load() }

    fun onAction(action: AdminMoviesAction) {
        when (action) {
            AdminMoviesAction.OnRetry -> load()
            AdminMoviesAction.OnLoadMore -> loadMore()
            AdminMoviesAction.OnAddClick -> _state.update { it.copy(form = MovieForm(), dialogError = null) }
            is AdminMoviesAction.OnEditClick -> openEdit(action.movie.id)
            is AdminMoviesAction.OnTitleChange -> updateForm { it.copy(title = action.value) }
            is AdminMoviesAction.OnDescriptionChange -> updateForm { it.copy(description = action.value) }
            is AdminMoviesAction.OnPosterUrlChange -> updateForm { it.copy(posterUrl = action.value) }
            is AdminMoviesAction.OnDurationChange -> updateForm { it.copy(duration = action.value.filter { c -> c.isDigit() }) }
            is AdminMoviesAction.OnReleasedDateChange -> updateForm { it.copy(releasedDate = action.value) }
            is AdminMoviesAction.OnToggleGenre -> updateForm { form ->
                form.copy(genreIds = if (action.genreId in form.genreIds) form.genreIds - action.genreId else form.genreIds + action.genreId)
            }
            AdminMoviesAction.OnSave -> save()
            AdminMoviesAction.OnDismissDialog -> _state.update { it.copy(form = null, dialogError = null) }
            is AdminMoviesAction.OnDeleteClick -> _state.update { it.copy(deleting = action.movie) }
            AdminMoviesAction.OnConfirmDelete -> delete()
            AdminMoviesAction.OnDismissDelete -> _state.update { it.copy(deleting = null) }
        }
    }

    private inline fun updateForm(transform: (MovieForm) -> MovieForm) {
        _state.update { it.copy(form = it.form?.let(transform), dialogError = null) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isLoadingMore = false, endReached = false, error = null) }
            // Genres back the form's chips; fetched once alongside the first page.
            catalog.getGenres().onSuccess { genres -> _state.update { it.copy(genres = genres) } }
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
        val offset = if (replace) 0 else _state.value.movies.size
        catalog.getMovies(limit = PAGE_SIZE, offset = offset)
            .onSuccess { page ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        movies = if (replace) page else it.movies + page,
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
                        // A load-more failure keeps the loaded rows; only a first-page failure blocks.
                        error = if (replace) resolveErrorText(message, error.toUiText()) else null,
                    )
                }
            }
    }

    private fun openEdit(id: Long) {
        viewModelScope.launch {
            catalog.getMovie(id)
                .onSuccess { movie ->
                    _state.update {
                        it.copy(
                            form = MovieForm(
                                editingId = movie.id,
                                title = movie.title,
                                description = movie.description,
                                posterUrl = movie.posterUrl,
                                duration = movie.duration.toString(),
                                releasedDate = movie.releasedDate.toString(),
                                genreIds = movie.genres.map { g -> g.id }.toSet(),
                            ),
                            dialogError = null,
                        )
                    }
                }
                .onFailure { error, message -> _state.update { it.copy(error = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private fun save() {
        val form = _state.value.form ?: return
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
            val result = if (form.editingId == null) catalog.createMovie(movie) else catalog.updateMovie(movie)
            result
                .onSuccess {
                    _state.update { it.copy(isSaving = false, form = null) }
                    load()
                }
                .onFailure { error, message -> _state.update { it.copy(isSaving = false, dialogError = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private fun delete() {
        val target = _state.value.deleting ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = null) }
            catalog.deleteMovie(target.id)
                .onSuccess { load() }
                .onFailure { error, message -> _state.update { it.copy(error = resolveErrorText(message, error.toUiText())) } }
        }
    }

    private fun String.toLocalDateOrToday(): LocalDate =
        takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).date

    private companion object {
        const val PAGE_SIZE = 50
    }
}
