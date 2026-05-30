package com.martdev.flickq.feature.movie.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martdev.flickq.core.common.onFailure
import com.martdev.flickq.core.common.onSuccess
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.core.presentation.toUiText
import com.martdev.flickq.feature.movie.domain.MovieRepository
import com.martdev.flickq.feature.movie.presentation.MovieUi
import com.martdev.flickq.feature.movie.presentation.toMovieUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovieListState(
    val movies: List<MovieUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiText? = null
)

sealed interface MovieListAction {
    data class OnMovieClick(val movieId: Long) : MovieListAction
    data object OnRetry : MovieListAction
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
        loadMovies()
    }

    fun onAction(action: MovieListAction) {
        when (action) {
            is MovieListAction.OnMovieClick -> viewModelScope.launch {
                _events.send(MovieListEvent.NavigateToDetail(action.movieId))
            }

            MovieListAction.OnRetry -> loadMovies()
        }
    }

    private fun loadMovies() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            movieRepository.getMovies()
                .onSuccess { movies ->
                    _state.update {
                        it.copy(isLoading = false, movies = movies.map { movie -> movie.toMovieUi() })
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toUiText()) }
                }
        }
    }
}
