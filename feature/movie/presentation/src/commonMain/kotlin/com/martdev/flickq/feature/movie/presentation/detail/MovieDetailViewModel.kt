package com.martdev.flickq.feature.movie.presentation.detail

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

data class MovieDetailState(
    val movie: MovieUi? = null,
    val isLoading: Boolean = false,
    val error: UiText? = null
)

sealed interface MovieDetailAction {
    data object OnRetry : MovieDetailAction
    data object OnBackClick : MovieDetailAction
}

sealed interface MovieDetailEvent {
    data object NavigateBack : MovieDetailEvent
}

class MovieDetailViewModel(
    private val movieId: Long,
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MovieDetailState())
    val state = _state.asStateFlow()

    private val _events = Channel<MovieDetailEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadMovie()
    }

    fun onAction(action: MovieDetailAction) {
        when (action) {
            MovieDetailAction.OnRetry -> loadMovie()
            MovieDetailAction.OnBackClick -> viewModelScope.launch {
                _events.send(MovieDetailEvent.NavigateBack)
            }
        }
    }

    private fun loadMovie() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            movieRepository.getMovieById(movieId)
                .onSuccess { movie ->
                    _state.update { it.copy(isLoading = false, movie = movie.toMovieUi()) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toUiText()) }
                }
        }
    }
}
