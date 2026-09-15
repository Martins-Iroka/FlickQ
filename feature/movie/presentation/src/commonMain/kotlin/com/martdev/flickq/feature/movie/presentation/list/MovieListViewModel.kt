package com.martdev.flickq.feature.movie.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.martdev.flickq.feature.movie.domain.MovieRepository
import com.martdev.flickq.feature.movie.presentation.list.MovieListEvent.NavigateToDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
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
    val selectedDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) {
    val isToday: Boolean get() = selectedDate == today
    val isTomorrow: Boolean get() = selectedDate == getTomorrow()

    private fun getTomorrow() =
        Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.DAY)
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

    val movieState: StateFlow<MovieListState>
        field = MutableStateFlow(MovieListState())

    private val _events = Channel<MovieListEvent>(
        Channel.BUFFERED
    )
    val events = _events.receiveAsFlow()

    private val pager = Pager(
        PagingConfig(
            pageSize = PAGE_SIZE
        )
    ) {
        MovieListPagingSource(
            movieRepository,
            movieState.value.selectedDate
        )
    }

    private val clock = Clock.System

    @OptIn(ExperimentalCoroutinesApi::class)
    val movieList = movieState
        .flatMapLatest {
            pager.flow
        }.cachedIn(viewModelScope)

    fun onAction(action: MovieListAction) {
        when (action) {
            is MovieListAction.OnMovieClick -> viewModelScope.launch {
                _events.send(NavigateToDetail(action.movieId))
            }

            MovieListAction.OnLoadMore -> {}
            MovieListAction.OnRetry -> pager.retry()
            is MovieListAction.OnDateSelected -> {
                println("This is the date selected ${action.date}")
                changeDate(action.date)
            }

            MovieListAction.OnTodayClick -> {
                val today = clock.todayIn(TimeZone.currentSystemDefault())
                println("This is today's date selected $today")
                changeDate(today)
            }

            MovieListAction.OnTomorrowClick -> {
                val tomorrow =
                    clock.todayIn(TimeZone.currentSystemDefault()).plus(
                        1,
                        DateTimeUnit.DAY
                    )
                println("This is tomorrow's date selected $tomorrow")
                changeDate(tomorrow)
            }
        }
    }

    private fun changeDate(date: LocalDate) {
        if (date != movieState.value.selectedDate) {
            movieState.update { it.copy(selectedDate = date) }
        }
    }

    private companion object {
        const val PAGE_SIZE = 6
    }
}
