package com.martdev.flickq.feature.showtime.presentation.list

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.showtime.domain.ShowtimeRepository
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private class FakeShowtimeRepository : ShowtimeRepository {
    private val base = Clock.System.now()
    private val showtimes = listOf(
        Showtime(id = 10, movieId = 1, roomId = 1, startsAt = base, endsAt = base + 2.hours, price = 3500),
        Showtime(
            id = 11, movieId = 1, roomId = 2, startsAt = base + 3.hours, endsAt = base + 5.hours,
            price = 3500, status = ShowtimeStatus.CANCELLED
        )
    )
    var shouldReturnError = false

    override suspend fun getShowtimesByMovieId(movieId: Long): Result<List<Showtime>, DataError> =
        if (shouldReturnError) Result.Error(DataError.Network.NO_INTERNET)
        else Result.Success(showtimes.filter { it.movieId == movieId })

    override suspend fun getShowtimeById(id: Long): Result<Showtime, DataError> =
        showtimes.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShowtimeListViewModelTest {

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
    fun `showtimes for the movie load into state on init`() = runTest {
        val viewModel = ShowtimeListViewModel(movieId = 1, showtimeRepository = FakeShowtimeRepository())

        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.showtimes).hasSize(2)
    }

    @Test
    fun `picking a scheduled showtime emits PickShowtime`() = runTest {
        val viewModel = ShowtimeListViewModel(movieId = 1, showtimeRepository = FakeShowtimeRepository())

        viewModel.events.test {
            viewModel.onAction(ShowtimeListAction.OnShowtimeClick(10))
            assertThat(awaitItem()).isEqualTo(ShowtimeListEvent.PickShowtime(10))
        }
    }

    @Test
    fun `cancelled showtimes are not selectable and emit nothing`() = runTest {
        val viewModel = ShowtimeListViewModel(movieId = 1, showtimeRepository = FakeShowtimeRepository())

        viewModel.events.test {
            viewModel.onAction(ShowtimeListAction.OnShowtimeClick(11))
            expectNoEvents()
        }
    }

    @Test
    fun `failure surfaces an error in state`() = runTest {
        val repo = FakeShowtimeRepository().apply { shouldReturnError = true }
        val viewModel = ShowtimeListViewModel(movieId = 1, showtimeRepository = repo)

        assertThat(viewModel.state.value.error).isNotNull()
    }
}
