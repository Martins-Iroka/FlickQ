package com.martdev.flickq.feature.movie.presentation.list

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.movie.domain.MovieRepository
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private class FakeMovieRepository(
    private val movies: List<Movie> = listOf(
        Movie(id = 1, title = "Neon Skyline", duration = 128, genres = listOf(Genre(1, "Sci-Fi"))),
        Movie(id = 2, title = "The Quiet Coast", duration = 112)
    )
) : MovieRepository {
    var shouldReturnError = false

    override suspend fun getMovies(): Result<List<Movie>, DataError> =
        if (shouldReturnError) Result.Error(DataError.Network.NO_INTERNET)
        else Result.Success(movies)

    override suspend fun getMovieById(id: Long): Result<Movie, DataError> =
        movies.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `movies load into state on init`() = runTest {
        val viewModel = MovieListViewModel(FakeMovieRepository())

        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.movies).hasSize(2)
        assertThat(state.movies.first().title).isEqualTo("Neon Skyline")
    }

    @Test
    fun `failure surfaces an error in state`() = runTest {
        val repo = FakeMovieRepository().apply { shouldReturnError = true }
        val viewModel = MovieListViewModel(repo)

        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNotNull()
    }

    @Test
    fun `clicking a movie emits NavigateToDetail`() = runTest {
        val viewModel = MovieListViewModel(FakeMovieRepository())

        viewModel.events.test {
            viewModel.onAction(MovieListAction.OnMovieClick(2))
            assertThat(awaitItem()).isEqualTo(MovieListEvent.NavigateToDetail(2))
        }
    }
}
