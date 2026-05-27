package com.martdev.flickq.features.reservation.infrastructure.db.repository

import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.auth.infrastructure.db.repository.UserRepositoryImpl
import com.martdev.flickq.features.auth.infrastructure.db.table.UserRefreshTokenTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserVerificationTable
import com.martdev.flickq.features.movie.domain.repository.GenreRepository
import com.martdev.flickq.features.movie.domain.repository.MovieRepository
import com.martdev.flickq.features.movie.infrasturcture.repository.GenreRepositoryImpl
import com.martdev.flickq.features.movie.infrasturcture.repository.MovieRepositoryImpl
import com.martdev.flickq.features.movie.infrasturcture.tables.GenresTable
import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.reservation.domain.repository.ReservationRepository
import com.martdev.flickq.features.reservation.domain.repository.ShowtimeSeatRepository
import com.martdev.flickq.features.reservation.infrastructure.db.table.ReservationTable
import com.martdev.flickq.features.reservation.infrastructure.db.table.ShowtimeSeatTable
import com.martdev.flickq.features.room.domain.repository.RoomRepository
import com.martdev.flickq.features.room.domain.repository.SeatRepository
import com.martdev.flickq.features.room.infrastructure.db.repository.RoomRepositoryImpl
import com.martdev.flickq.features.room.infrastructure.db.repository.SeatRepositoryImpl
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.features.showtime.infrastructure.db.repository.ShowtimeRepositoryImpl
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeTable
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Testcontainers
class ReservationRepositoryImplTest {

    companion object {
        private lateinit var repo: ReservationRepository
        private lateinit var showtimeSeatRepo: ShowtimeSeatRepository
        private lateinit var showtimeRepo: ShowtimeRepository
        private lateinit var roomRepo: RoomRepository
        private lateinit var seatRepo: SeatRepository
        private lateinit var movieRepo: MovieRepository
        private lateinit var genreRepo: GenreRepository
        private lateinit var userRepo: UserRepository
        private val clock = Clock.System.now()

        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            repo = ReservationRepositoryImpl()
            showtimeSeatRepo = ShowtimeSeatRepositoryImpl()
            showtimeRepo = ShowtimeRepositoryImpl()
            roomRepo = RoomRepositoryImpl()
            seatRepo = SeatRepositoryImpl()
            movieRepo = MovieRepositoryImpl()
            genreRepo = GenreRepositoryImpl()
            userRepo = UserRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            ShowtimeSeatTable.deleteAll()
            ReservationTable.deleteAll()
            ShowtimeTable.deleteAll()
            RoomTable.deleteAll()
            MoviesTable.deleteAll()
            GenresTable.deleteAll()
            UserVerificationTable.deleteAll()
            UserRefreshTokenTable.deleteAll()
            UserTable.deleteAll()
        }
    }

    @Test
    fun `create reservation successfully holds seats and returns PENDING reservation`() = runTest {
        val ctx = setupContext(seatCount = 3)

        val result = repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 15000,
                expiresAt = clock.plus(15.minutes)
            ),
            ctx.seatIds
        )

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(ctx.userId, result.value.userId)
        assertEquals(ctx.showtimeId, result.value.showtimeId)
        assertEquals(ReservationStatus.PENDING, result.value.status)
        assertEquals(15000L, result.value.totalAmount)
        assertEquals(3, result.value.seats.size)
        assertTrue(result.value.seats.all { it.status == SeatStatus.HELD })
        assertTrue(result.value.seats.all { it.reservationId == result.value.id })
    }

    @Test
    fun `create reservation fails with NotFound when seats do not exist for showtime`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val nonExistentSeatId = ctx.seatIds.max() + 9999

        val result = repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            ctx.seatIds + nonExistentSeatId
        )

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `create reservation fails with Conflict when a seat is not available`() = runTest {
        val ctx = setupContext(seatCount = 2)
        transaction {
            ShowtimeSeatTable.update({
                (ShowtimeSeatTable.showtimeId eq ctx.showtimeId) and
                        (ShowtimeSeatTable.seatId eq ctx.seatIds.first())
            }) {
                it[ShowtimeSeatTable.status] = SeatStatus.HELD
            }
        }

        val result = repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            ctx.seatIds
        )

        assertTrue(result is DataResult.Failure.Conflict, result.toString())
    }

    @Test
    fun `get reservation by id returns the reservation`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val created = (repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            ctx.seatIds
        ) as DataResult.Success).value

        val result = repo.getReservationById(created.id)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(created.id, result.value.id)
        assertEquals(ctx.userId, result.value.userId)
    }

    @Test
    fun `get reservation by id returns NotFound when reservation does not exist`() = runTest {
        val result = repo.getReservationById(Random.nextLong(1_000_000, 2_000_000))

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `get reservations by user id returns user's reservations`() = runTest {
        val ctx = setupContext(seatCount = 4)
        repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 5000,
                expiresAt = clock.plus(15.minutes)
            ),
            listOf(ctx.seatIds[0])
        )
        repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            listOf(ctx.seatIds[1], ctx.seatIds[2])
        )

        val result = repo.getReservationsByUserId(ctx.userId)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.userId == ctx.userId })
    }

    @Test
    fun `get reservations by user id returns empty list when user has none`() = runTest {
        val result = repo.getReservationsByUserId(Random.nextLong(1_000_000, 2_000_000))

        assertTrue(result is DataResult.Success, result.toString())
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `get all reservations returns paginated reservations`() = runTest {
        val ctx = setupContext(seatCount = 3)
        repeat(3) { i ->
            repo.createReservation(
                Reservation(
                    userId = ctx.userId,
                    showtimeId = ctx.showtimeId,
                    totalAmount = (i + 1).toLong() * 5000,
                    expiresAt = clock.plus(15.minutes)
                ),
                listOf(ctx.seatIds[i])
            )
        }

        val firstPage = repo.getAllReservations(limit = 2, offset = 0L)
        val secondPage = repo.getAllReservations(limit = 2, offset = 2L)

        assertTrue(firstPage is DataResult.Success, firstPage.toString())
        assertTrue(secondPage is DataResult.Success, secondPage.toString())
        assertEquals(2, firstPage.value.size)
        assertEquals(1, secondPage.value.size)
    }

    @Test
    fun `update reservation status to CONFIRMED books the seats`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val created = (repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            ctx.seatIds
        ) as DataResult.Success).value

        val result = repo.updateReservationStatus(created.id, ReservationStatus.CONFIRMED)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(ReservationStatus.CONFIRMED, result.value.status)
        assertEquals(ctx.seatIds.size, result.value.seats.size)
        assertTrue(result.value.seats.all { it.status == SeatStatus.BOOKED })
    }

    @Test
    fun `update reservation status to CANCELLED releases the seats`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val created = (repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            ctx.seatIds
        ) as DataResult.Success).value

        val result = repo.updateReservationStatus(created.id, ReservationStatus.CANCELLED)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(ReservationStatus.CANCELLED, result.value.status)
        assertTrue(result.value.seats.isEmpty())

        val showtimeSeats = (showtimeSeatRepo.getAllSeatsByShowtime(ctx.showtimeId) as DataResult.Success).value
        assertTrue(showtimeSeats.all { it.status == SeatStatus.AVAILABLE && it.reservationId == null })
    }

    @Test
    fun `update reservation status to PENDING does not change seat status`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val created = (repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            ctx.seatIds
        ) as DataResult.Success).value

        val result = repo.updateReservationStatus(created.id, ReservationStatus.PENDING)

        assertTrue(result is DataResult.Success, result.toString())
        assertTrue(result.value.seats.all { it.status == SeatStatus.HELD })
    }

    @Test
    fun `update reservation status returns NotFound when reservation does not exist`() = runTest {
        val result = repo.updateReservationStatus(
            id = Random.nextLong(1_000_000, 2_000_000),
            status = ReservationStatus.CONFIRMED
        )

        assertTrue(result is DataResult.Failure.NotFound, result.toString())
    }

    @Test
    fun `cancel expired reservations cancels expired PENDING reservations and releases seats`() = runTest {
        val ctx = setupContext(seatCount = 4)
        val expired = (repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 5000,
                expiresAt = clock.minus(1.hours)
            ),
            listOf(ctx.seatIds[0], ctx.seatIds[1])
        ) as DataResult.Success).value
        val active = (repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            listOf(ctx.seatIds[2], ctx.seatIds[3])
        ) as DataResult.Success).value

        val result = repo.cancelExpiredReservation()

        assertTrue(result is DataResult.Success, result.toString())

        val expiredAfter = (repo.getReservationById(expired.id) as DataResult.Success).value
        val activeAfter = (repo.getReservationById(active.id) as DataResult.Success).value

        assertEquals(ReservationStatus.CANCELLED, expiredAfter.status)
        assertEquals(ReservationStatus.PENDING, activeAfter.status)

        val allSeats = (showtimeSeatRepo.getAllSeatsByShowtime(ctx.showtimeId) as DataResult.Success).value
        val expiredSeatIds = listOf(ctx.seatIds[0], ctx.seatIds[1])
        val activeSeatIds = listOf(ctx.seatIds[2], ctx.seatIds[3])

        assertTrue(allSeats.filter { it.seatId in expiredSeatIds }
            .all { it.status == SeatStatus.AVAILABLE && it.reservationId == null })
        assertTrue(allSeats.filter { it.seatId in activeSeatIds }
            .all { it.status == SeatStatus.HELD && it.reservationId == active.id })
    }

    @Test
    fun `cancel expired reservations does nothing when nothing has expired`() = runTest {
        val ctx = setupContext(seatCount = 2)
        val active = (repo.createReservation(
            Reservation(
                userId = ctx.userId,
                showtimeId = ctx.showtimeId,
                totalAmount = 10000,
                expiresAt = clock.plus(15.minutes)
            ),
            ctx.seatIds
        ) as DataResult.Success).value

        val result = repo.cancelExpiredReservation()

        assertTrue(result is DataResult.Success, result.toString())
        val activeAfter = (repo.getReservationById(active.id) as DataResult.Success).value
        assertEquals(ReservationStatus.PENDING, activeAfter.status)
        assertTrue(activeAfter.seats.all { it.status == SeatStatus.HELD })
    }

    private data class TestContext(
        val userId: Long,
        val showtimeId: Long,
        val seatIds: List<Long>
    )

    private suspend fun setupContext(seatCount: Int): TestContext {
        val n = Random.nextInt(100_000)
        val user = UserData(email = "user_$n@test.com", password = "password")
        val savedUser = (userRepo.saveUserAndVerificationToken(user, "verify_token_$n") as DataResult.Success).value

        val genre = Genre(name = "Genre $n")
        val savedGenre = (genreRepo.saveGenre(genre) as DataResult.Success).value
        val movie = Movie(
            title = "Movie $n",
            description = "Description",
            posterUrl = "poster_url",
            genres = listOf(savedGenre)
        )
        val movieId = (movieRepo.createMovie(movie) as DataResult.Success).value
        val room = Room(name = "Room $n", rows = 5, columns = 10)
        val savedRoom = (roomRepo.createRoom(room) as DataResult.Success).value
        val showtime = Showtime(
            movieId = movieId,
            roomId = savedRoom.id,
            startsAt = clock,
            endsAt = clock.plus(2.hours),
            price = 5000
        )
        val showtimeId = (showtimeRepo.createShowtime(showtime) as DataResult.Success).value.id

        val seats = (1..seatCount).map { i ->
            Seat(
                roomId = savedRoom.id,
                rowLabel = "A",
                seatNumber = i
            )
        }
        val seatIds = (seatRepo.createSeats(seats) as DataResult.Success).value.map { it.id }

        showtimeSeatRepo.populateShowtimeSeats(showtimeId, seatIds)

        return TestContext(savedUser.id, showtimeId, seatIds)
    }
}