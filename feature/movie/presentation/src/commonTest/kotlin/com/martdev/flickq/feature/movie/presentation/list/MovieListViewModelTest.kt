package com.martdev.flickq.feature.movie.presentation.list

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
import kotlinx.datetime.LocalDate
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
    val requestedPages = mutableListOf<Pair<Int, Int>>() // (limit, offset)

    override suspend fun getMovies(
        limit: Int,
        offset: Int,
        date: LocalDate?
    ): Result<List<Movie>, DataError> {
        requestedPages += limit to offset
        return if (shouldReturnError) Result.Error(DataError.Network.NO_INTERNET)
        else Result.Success(movies.drop(offset).take(limit))
    }

    override suspend fun getMovieById(id: Long): Result<Movie, DataError> =
        movies.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)
}

private fun movies(count: Int): List<Movie> =
    (1..count).map { Movie(id = it.toLong(), title = "Movie $it", duration = 100) }

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
    fun `paginates with load-more until the catalog is exhausted`() = runTest {
        val repo = FakeMovieRepository(movies(45))
        val vm = MovieListViewModel(repo)

        assertThat(vm.state.value.movies).hasSize(20)
        assertThat(vm.state.value.endReached).isFalse()

        vm.onAction(MovieListAction.OnLoadMore)
        assertThat(vm.state.value.movies).hasSize(40)
        assertThat(vm.state.value.endReached).isFalse()

        vm.onAction(MovieListAction.OnLoadMore)
        assertThat(vm.state.value.movies).hasSize(45)
        assertThat(vm.state.value.endReached).isTrue()

        // Exhausted: a further load-more is a no-op (no extra page fetched).
        vm.onAction(MovieListAction.OnLoadMore)
        assertThat(repo.requestedPages).isEqualTo(listOf(20 to 0, 20 to 20, 20 to 40))
    }

    @Test
    fun `a short first page marks the end immediately`() = runTest {
        val vm = MovieListViewModel(FakeMovieRepository(movies(5)))

        assertThat(vm.state.value.movies).hasSize(5)
        assertThat(vm.state.value.endReached).isTrue()
        assertThat(vm.state.value.canLoadMore).isFalse()
    }

    @Test
    fun `a load-more failure keeps loaded movies without a blocking error`() = runTest {
        val repo = FakeMovieRepository(movies(45))
        val vm = MovieListViewModel(repo)
        assertThat(vm.state.value.movies).hasSize(20)

        repo.shouldReturnError = true
        vm.onAction(MovieListAction.OnLoadMore)

        assertThat(vm.state.value.movies).hasSize(20) // already-loaded page retained
        assertThat(vm.state.value.error).isNull()     // not a full-screen error
        assertThat(vm.state.value.isLoadingMore).isFalse()
        assertThat(vm.state.value.canLoadMore).isTrue() // user can retry
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
