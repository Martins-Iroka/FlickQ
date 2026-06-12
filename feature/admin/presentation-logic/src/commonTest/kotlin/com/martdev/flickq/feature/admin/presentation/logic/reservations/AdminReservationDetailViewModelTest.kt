package com.martdev.flickq.feature.admin.presentation.logic.reservations

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.feature.admin.domain.AdminPaymentRepository
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
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

private class FakeAdminReservationRepository(
    private var reservation: Reservation = Reservation(id = 5, showtimeId = 1, status = ReservationStatus.CONFIRMED, totalAmount = 7000),
    private val getFails: Boolean = false,
) : AdminReservationRepository {
    var cancelCount = 0

    override suspend fun getReservations(limit: Int, offset: Int): Result<List<Reservation>, DataError> =
        if (getFails) Result.Error(DataError.Network.UNKNOWN) else Result.Success(listOf(reservation))

    override suspend fun getReservation(id: Long): Result<Reservation, DataError> =
        if (getFails) Result.Error(DataError.Network.NOT_FOUND) else Result.Success(reservation)

    override suspend fun cancelReservation(id: Long): Result<Reservation, DataError> {
        cancelCount++
        reservation = reservation.copy(status = ReservationStatus.CANCELLED)
        return Result.Success(reservation)
    }

    override suspend fun populateSeats(showtimeId: Long): EmptyResult<DataError> = Result.Success(Unit)
}

private class FakeAdminPaymentRepository(
    private val payments: List<Payment> = emptyList(),
    private val fails: Boolean = false,
) : AdminPaymentRepository {
    override suspend fun getPaymentsByReservation(reservationId: Long): Result<List<Payment>, DataError> =
        if (fails) Result.Error(DataError.Network.UNKNOWN) else Result.Success(payments)
}

/** Context joins are best-effort, so the default stub answers every lookup. */
private class FakeAdminCatalogRepository(
    val showtimes: List<Showtime> = listOf(Showtime(id = 1, movieId = 9, roomId = 3)),
    val seats: List<Seat> = listOf(Seat(id = 101, roomId = 3, rowLabel = "H", seatNumber = 12)),
) : AdminCatalogRepository {
    override suspend fun getMovies(limit: Int, offset: Int): Result<List<Movie>, DataError> = Result.Success(emptyList())
    override suspend fun getMovie(id: Long): Result<Movie, DataError> = Result.Success(Movie(id = id, title = "Movie $id"))
    override suspend fun createMovie(movie: Movie): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun updateMovie(movie: Movie): Result<Movie, DataError> = Result.Success(movie)
    override suspend fun deleteMovie(id: Long): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun getGenres(): Result<List<Genre>, DataError> = Result.Success(emptyList())
    override suspend fun createGenre(genre: Genre): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun deleteGenre(id: Long): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun getRooms(): Result<List<Room>, DataError> = Result.Success(listOf(Room(3, "Screen 3", 8, 10)))
    override suspend fun createRoom(room: Room): Result<Room, DataError> = Result.Success(room)
    override suspend fun updateRoom(room: Room): Result<Room, DataError> = Result.Success(room)
    override suspend fun deleteRoom(id: Long): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun getSeats(roomId: Long): Result<List<Seat>, DataError> = Result.Success(seats)
    override suspend fun createSeats(seats: List<Seat>): Result<List<Seat>, DataError> = Result.Success(seats)
    override suspend fun getShowtimes(limit: Int, offset: Int): Result<List<Showtime>, DataError> = Result.Success(showtimes)
    override suspend fun createShowtime(showtime: Showtime): Result<Showtime, DataError> = Result.Success(showtime)
    override suspend fun updateShowtime(showtime: Showtime): Result<Showtime, DataError> = Result.Success(showtime)
    override suspend fun deleteShowtime(id: Long): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun updateShowtimeStatus(id: Long, status: ShowtimeStatus): Result<Showtime, DataError> =
        Result.Success(Showtime(id = id, status = status))
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReservationDetailViewModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loads the reservation and its payments`() = runTest {
        val payments = listOf(Payment(id = 1, reservationId = 5, reference = "FQ-1", status = PaymentStatus.SUCCESS))
        val vm = AdminReservationDetailViewModel(5, FakeAdminReservationRepository(), FakeAdminPaymentRepository(payments), FakeAdminCatalogRepository())

        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.reservation).isNotNull()
        assertThat(vm.state.value.payments).hasSize(1)
        assertThat(vm.state.value.canCancel).isTrue()
    }

    @Test
    fun `joins showtime, movie, room and seat layout for the reservation`() = runTest {
        val vm = AdminReservationDetailViewModel(5, FakeAdminReservationRepository(), FakeAdminPaymentRepository(), FakeAdminCatalogRepository())

        assertThat(vm.state.value.showtime?.id).isEqualTo(1L)
        assertThat(vm.state.value.movie?.title).isEqualTo("Movie 9")
        assertThat(vm.state.value.room?.name).isEqualTo("Screen 3")
        assertThat(vm.state.value.seat(101)?.rowLabel).isEqualTo("H")
    }

    @Test
    fun `cancelling flips the status and is no longer cancellable`() = runTest {
        val repo = FakeAdminReservationRepository()
        val vm = AdminReservationDetailViewModel(5, repo, FakeAdminPaymentRepository(), FakeAdminCatalogRepository())

        vm.onAction(AdminReservationDetailAction.OnCancelClick)
        vm.onAction(AdminReservationDetailAction.OnConfirmCancel)

        assertThat(repo.cancelCount).isEqualTo(1)
        assertThat(vm.state.value.reservation?.status).isEqualTo(ReservationStatus.CANCELLED)
        assertThat(vm.state.value.canCancel).isFalse()
    }

    @Test
    fun `a payments lookup failure keeps the reservation visible`() = runTest {
        val vm = AdminReservationDetailViewModel(5, FakeAdminReservationRepository(), FakeAdminPaymentRepository(fails = true), FakeAdminCatalogRepository())

        assertThat(vm.state.value.reservation).isNotNull()
        assertThat(vm.state.value.message).isNotNull()
    }
}
