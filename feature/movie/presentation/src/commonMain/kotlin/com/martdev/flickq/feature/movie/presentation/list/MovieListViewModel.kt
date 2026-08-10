package com.martdev.flickq.feature.movie.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.resolveErrorText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.movie.domain.MovieRepository
import com.martdev.flickq.feature.movie.presentation.MovieUi
import com.martdev.flickq.feature.movie.presentation.list.MovieListEvent.NavigateToDetail
import com.martdev.flickq.feature.movie.presentation.list.MovieListViewModel.Companion.PAGE_SIZE
import com.martdev.flickq.feature.movie.presentation.toMovieUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class MovieListState(
    val movies: List<MovieUi> = emptyList(),
    val isLoading: Boolean = false,      // first-page / full-screen load
    val isLoadingMore: Boolean = false,  // appending a subsequent page
    val endReached: Boolean = false,     // last page returned fewer than a full page
    val error: UiText? = null,
    val selectedDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
) {
    /** Show a "load more" affordance only when there's more to fetch, and we're idle. */
    val canLoadMore: Boolean get() = !isLoading && !isLoadingMore && !endReached && error == null
    val isToday: Boolean get() = selectedDate == getToday()
    val isTomorrow: Boolean get() = selectedDate == getTomorrow()

    private fun getToday() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun getTomorrow() = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.DAY)
}

sealed interface MovieListAction {
    data class OnMovieClick(val movieId: Long) : MovieListAction
    data object OnLoadMore : MovieListAction
    data object OnRetry : MovieListAction
    data object OnTodayClick : MovieListAction
    data object OnTomorrowClick : MovieListAction
    data class OnDateSelected(val date: LocalDate) : MovieListAction
}

sealed interface MovieListEvent {
    data class NavigateToDetail(val movieId: Long) : MovieListEvent
}

class MovieListViewModel(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MovieListState())
    val state = _state.asStateFlow()

    private val _events = Channel<MovieListEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadFirstPage()
    }

    fun onAction(action: MovieListAction) {
        when (action) {
            is MovieListAction.OnMovieClick -> viewModelScope.launch {
                _events.send(NavigateToDetail(action.movieId))
            }

            MovieListAction.OnLoadMore -> loadMore()
            MovieListAction.OnRetry -> loadFirstPage()
            is MovieListAction.OnDateSelected -> {
                println("This is the date selected ${action.date}")
                changeDate(action.date)
            }
            MovieListAction.OnTodayClick -> {
                val date = Clock.System.todayIn(TimeZone.currentSystemDefault())
                println("This is today's date selected $date")
                changeDate(date)
            }

            MovieListAction.OnTomorrowClick -> {
                val date =
                    Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(
                        1,
                        DateTimeUnit.DAY
                    )
                println("This is tomorrow's date selected $date")
                changeDate(date)
            }
        }
    }

    private fun changeDate(date: LocalDate) {
        if (date != state.value.selectedDate) {
            _state.update { it.copy(selectedDate = date) }
            loadFirstPage()
        }
    }

    private fun loadFirstPage() {
        _state.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                endReached = false
            )
        }
        viewModelScope.launch { fetchPage(replace = true) }
    }

    private fun loadMore() {
        if (!_state.value.canLoadMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { fetchPage(replace = false) }
    }

    /**
     * Fetches the page at the current offset. [replace] true seeds the first page (and shows the
     * full-screen spinner); false appends. A short page (< [PAGE_SIZE]) means the catalog is
     * exhausted. A load-more failure keeps the already-loaded movies and just stops appending, so
     * the user can retry; only a first-page failure blocks the screen.
     */
    private suspend fun fetchPage(replace: Boolean) {
        val offset = if (replace) 0 else _state.value.movies.size
        val date = state.value.selectedDate
        movieRepository.getMovies(limit = PAGE_SIZE, offset = offset, date)
            .onSuccess { page ->
                val ui = page.map { it.toMovieUi() }
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        movies = if (replace) ui else it.movies + ui,
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
        const val PAGE_SIZE = 20
    }
}
