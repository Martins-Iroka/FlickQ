package com.martdev.flickq.feature.booking.presentation.seat

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.booking.domain.BookingRepository
import com.martdev.flickq.feature.booking.domain.SeatAvailability
import com.martdev.flickq.feature.booking.domain.SeatMap
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.room.model.Seat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private class FakeBookingRepository : BookingRepository {
    var reservationFails = false
    var lastSeatIds: List<Long>? = null

    private fun seat(id: Long, row: String, num: Int, occupied: Boolean) =
        SeatAvailability(
            seat = Seat(id = id, roomId = 1, rowLabel = row, seatNumber = num),
            status = if (occupied) SeatStatus.BOOKED else SeatStatus.AVAILABLE
        )

    override suspend fun getSeatMap(showtimeId: Long): Result<SeatMap, DataError> =
        Result.Success(
            SeatMap(
                showtimeId = showtimeId,
                rows = 2,
                columns = 2,
                seatPrice = 3500,
                seats = listOf(
                    seat(1, "A", 1, occupied = false),
                    seat(2, "A", 2, occupied = true),
                    seat(3, "B", 1, occupied = false),
                    seat(4, "B", 2, occupied = false)
                )
            )
        )

    override suspend fun createReservation(showtimeId: Long, seatIds: List<Long>): Result<Reservation, DataError> {
        lastSeatIds = seatIds
        return if (reservationFails) Result.Error(DataError.Network.CONFLICT)
        else Result.Success(Reservation(id = 99, showtimeId = showtimeId, totalAmount = seatIds.size * 3500L))
    }

    override suspend fun getReservation(id: Long): Result<Reservation, DataError> =
        Result.Success(Reservation(id = id))
}

@OptIn(ExperimentalCoroutinesApi::class)
class SeatSelectionViewModelTest {

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
    fun `seat map loads with grid dimensions`() = runTest {
        val viewModel = SeatSelectionViewModel(showtimeId = 1, bookingRepository = FakeBookingRepository())

        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.rows).isEqualTo(2)
        assertThat(state.columns).isEqualTo(2)
    }

    @Test
    fun `tapping an available seat toggles selection and total`() = runTest {
        val viewModel = SeatSelectionViewModel(showtimeId = 1, bookingRepository = FakeBookingRepository())

        viewModel.onAction(SeatSelectionAction.OnSeatClick(1))
        assertThat(viewModel.state.value.selectedIds).isEqualTo(setOf(1L))
        assertThat(viewModel.state.value.totalAmount).isEqualTo(3500L)

        viewModel.onAction(SeatSelectionAction.OnSeatClick(1))
        assertThat(viewModel.state.value.selectedIds).isEqualTo(emptySet())
    }

    @Test
    fun `tapping an occupied seat does nothing`() = runTest {
        val viewModel = SeatSelectionViewModel(showtimeId = 1, bookingRepository = FakeBookingRepository())

        viewModel.onAction(SeatSelectionAction.OnSeatClick(2))
        assertThat(viewModel.state.value.selectedIds).isEqualTo(emptySet())
    }

    @Test
    fun `reserving selected seats emits ReservationCreated`() = runTest {
        val repo = FakeBookingRepository()
        val viewModel = SeatSelectionViewModel(showtimeId = 1, bookingRepository = repo)
        viewModel.onAction(SeatSelectionAction.OnSeatClick(1))
        viewModel.onAction(SeatSelectionAction.OnSeatClick(3))

        viewModel.events.test {
            viewModel.onAction(SeatSelectionAction.OnReserveClick)
            assertThat(awaitItem()).isEqualTo(SeatSelectionEvent.ReservationCreated(99))
        }
        assertThat(repo.lastSeatIds).isNotNull()
        assertThat(repo.lastSeatIds!!.size).isEqualTo(2)
    }

    @Test
    fun `reservation conflict surfaces an error`() = runTest {
        val repo = FakeBookingRepository().apply { reservationFails = true }
        val viewModel = SeatSelectionViewModel(showtimeId = 1, bookingRepository = repo)
        viewModel.onAction(SeatSelectionAction.OnSeatClick(1))

        viewModel.onAction(SeatSelectionAction.OnReserveClick)

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.isReserving).isFalse()
    }

    @Test
    fun `seat price drives total for multiple seats`() = runTest {
        val viewModel = SeatSelectionViewModel(showtimeId = 1, bookingRepository = FakeBookingRepository())
        viewModel.onAction(SeatSelectionAction.OnSeatClick(1))
        viewModel.onAction(SeatSelectionAction.OnSeatClick(3))
        viewModel.onAction(SeatSelectionAction.OnSeatClick(4))

        assertThat(viewModel.state.value.selectedCount).isEqualTo(3)
        assertThat(viewModel.state.value.totalAmount).isGreaterThan(0L)
        assertThat(viewModel.state.value.canReserve).isTrue()
    }
}
