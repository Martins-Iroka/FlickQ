package com.martdev.flickq.feature.movie.presentation.list

import androidx.paging.PagingSource
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.movie.domain.MovieRepository
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.movie.model.MovieDataModel
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

/** Pages like `/showtime/showtime-movies`: nextOffset is offset + limit, or -1 on the last page. */
private class FakeMovieRepository(
    private val movies: List<Movie> = movies(2),
) : MovieRepository {
    var shouldReturnError = false
    val requests = mutableListOf<Triple<LocalDate, Int, Long>>() // (date, limit, offset)

    override suspend fun getMovieById(id: Long): Result<Movie, DataError> =
        movies.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)

    override suspend fun getScheduledMovies(
        date: LocalDate,
        limit: Int,
        offset: Long
    ): Result<MovieDataModel, DataError> {
        requests += Triple(date, limit, offset)
        if (shouldReturnError) return Result.Error(DataError.Network.NO_INTERNET, "offline")
        val page = movies.drop(offset.toInt()).take(limit)
        val nextOffset = if (page.size < limit) -1L else offset + limit
        return Result.Success(MovieDataModel(page, nextOffset))
    }
}

private fun movies(count: Int): List<Movie> =
    (1..count).map { Movie(id = it.toLong(), title = "Movie $it", duration = 100) }

private val DATE = LocalDate(2026, 7, 4)

private fun refresh() = PagingSource.LoadParams.Refresh<Long>(key = null, loadSize = 6, placeholdersEnabled = false)
private fun append(key: Long) = PagingSource.LoadParams.Append(key = key, loadSize = 6, placeholdersEnabled = false)

class MovieListPagingSourceTest {

    @Test
    fun `the first page loads offset 0 for the selected date`() = runTest {
        val repo = FakeMovieRepository(movies(10))

        val page = MovieListPagingSource(repo, DATE).load(refresh())

        assertThat(page).isInstanceOf(PagingSource.LoadResult.Page::class)
        page as PagingSource.LoadResult.Page
        assertThat(page.data.map { it.id }).containsExactly(1L, 2L, 3L, 4L, 5L, 6L)
        assertThat(page.prevKey).isNull()
        assertThat(page.nextKey).isEqualTo(6L)
        assertThat(repo.requests).containsExactly(Triple(DATE, 6, 0L))
    }

    @Test
    fun `an append uses its key as the offset and the last page has no next key`() = runTest {
        val repo = FakeMovieRepository(movies(10))

        val page = MovieListPagingSource(repo, DATE).load(append(6L)) as PagingSource.LoadResult.Page

        assertThat(page.data.map { it.id }).containsExactly(7L, 8L, 9L, 10L)
        assertThat(page.nextKey).isNull()
        assertThat(repo.requests).containsExactly(Triple(DATE, 6, 6L))
    }

    @Test
    fun `a repository error becomes a load error`() = runTest {
        val repo = FakeMovieRepository().apply { shouldReturnError = true }

        val result = MovieListPagingSource(repo, DATE).load(refresh())

        assertThat(result).isInstanceOf(PagingSource.LoadResult.Error::class)
    }
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
    fun `starts on today`() = runTest {
        val state = MovieListViewModel(FakeMovieRepository()).movieState.value

        assertThat(state.isToday).isTrue()
        assertThat(state.isTomorrow).isFalse()
    }

    @Test
    fun `tomorrow and today chips move the selected date`() = runTest {
        val viewModel = MovieListViewModel(FakeMovieRepository())

        viewModel.onAction(MovieListAction.OnTomorrowClick)
        assertThat(viewModel.movieState.value.isTomorrow).isTrue()

        viewModel.onAction(MovieListAction.OnTodayClick)
        assertThat(viewModel.movieState.value.isToday).isTrue()
    }

    @Test
    fun `picking a date selects it`() = runTest {
        val viewModel = MovieListViewModel(FakeMovieRepository())

        viewModel.onAction(MovieListAction.OnDateSelected(DATE))

        assertThat(viewModel.movieState.value.selectedDate).isEqualTo(DATE)
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
