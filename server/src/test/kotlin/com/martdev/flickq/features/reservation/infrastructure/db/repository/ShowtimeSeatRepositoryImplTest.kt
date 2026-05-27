package com.martdev.flickq.features.reservation.infrastructure.db.repository

import com.martdev.flickq.features.movie.domain.repository.GenreRepository
import com.martdev.flickq.features.movie.domain.repository.MovieRepository
import com.martdev.flickq.features.movie.infrasturcture.repository.GenreRepositoryImpl
import com.martdev.flickq.features.movie.infrasturcture.repository.MovieRepositoryImpl
import com.martdev.flickq.features.movie.infrasturcture.tables.GenresTable
import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.reservation.domain.repository.ShowtimeSeatRepository
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

@Testcontainers
class ShowtimeSeatRepositoryImplTest {

    companion object {
        private lateinit var repo: ShowtimeSeatRepository
        private lateinit var showtimeRepo: ShowtimeRepository
        private lateinit var roomRepo: RoomRepository
        private lateinit var seatRepo: SeatRepository
        private lateinit var movieRepo: MovieRepository
        private lateinit var genreRepo: GenreRepository
        private val clock = Clock.System.now()

        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            repo = ShowtimeSeatRepositoryImpl()
            showtimeRepo = ShowtimeRepositoryImpl()
            roomRepo = RoomRepositoryImpl()
            seatRepo = SeatRepositoryImpl()
            movieRepo = MovieRepositoryImpl()
            genreRepo = GenreRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            ShowtimeSeatTable.deleteAll()
            ShowtimeTable.deleteAll()
            RoomTable.deleteAll()
            MoviesTable.deleteAll()
            GenresTable.deleteAll()
        }
    }

    @Test
    fun `populate showtime seats successfully`() = runTest {
        val (showtimeId, seatIds) = createShowtimeWithSeats(seatCount = 5)

        val result = repo.populateShowtimeSeats(showtimeId, seatIds)

        assertTrue(result is DataResult.Success, result.toString())
    }

    @Test
    fun `populate showtime seats with duplicate seat ids should fail`() = runTest {
        val (showtimeId, seatIds) = createShowtimeWithSeats(seatCount = 3)
        repo.populateShowtimeSeats(showtimeId, seatIds)

        val result = repo.populateShowtimeSeats(showtimeId, seatIds)

        assertTrue(result is DataResult.Failure.UniqueViolation, result.toString())
    }

    @Test
    fun `get available seats returns all seats when all are available`() = runTest {
        val (showtimeId, seatIds) = createShowtimeWithSeats(seatCount = 5)
        repo.populateShowtimeSeats(showtimeId, seatIds)

        val result = repo.getAvailableSeats(showtimeId)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(5, result.value.size)
        assertTrue(result.value.all { it.status == SeatStatus.AVAILABLE })
    }

    @Test
    fun `get available seats excludes held seats`() = runTest {
        val (showtimeId, seatIds) = createShowtimeWithSeats(seatCount = 3)
        repo.populateShowtimeSeats(showtimeId, seatIds)

        transaction {
            ShowtimeSeatTable.update({
                (ShowtimeSeatTable.showtimeId eq showtimeId) and
                        (ShowtimeSeatTable.seatId eq seatIds.first())
            }) {
                it[ShowtimeSeatTable.status] = SeatStatus.HELD
            }
        }

        val result = repo.getAvailableSeats(showtimeId)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.status == SeatStatus.AVAILABLE })
    }

    @Test
    fun `get available seats returns empty list when no seats have been populated`() = runTest {
        val (showtimeId, _) = createShowtimeWithSeats(seatCount = 0)

        val result = repo.getAvailableSeats(showtimeId)

        assertTrue(result is DataResult.Success, result.toString())
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `get available seats returns only seats for the requested showtime`() = runTest {
        val (showtimeId1, seatIds1) = createShowtimeWithSeats(seatCount = 3)
        val (showtimeId2, seatIds2) = createShowtimeWithSeats(seatCount = 2)
        repo.populateShowtimeSeats(showtimeId1, seatIds1)
        repo.populateShowtimeSeats(showtimeId2, seatIds2)

        val result = repo.getAvailableSeats(showtimeId1)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(3, result.value.size)
        assertTrue(result.value.all { it.showtimeId == showtimeId1 })
    }

    @Test
    fun `get all seats by showtime returns all seats regardless of status`() = runTest {
        val (showtimeId, seatIds) = createShowtimeWithSeats(seatCount = 3)
        repo.populateShowtimeSeats(showtimeId, seatIds)

        transaction {
            ShowtimeSeatTable.update({
                (ShowtimeSeatTable.showtimeId eq showtimeId) and
                        (ShowtimeSeatTable.seatId eq seatIds.first())
            }) {
                it[ShowtimeSeatTable.status] = SeatStatus.HELD
            }
        }

        val result = repo.getAllSeatsByShowtime(showtimeId)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(3, result.value.size)
    }

    @Test
    fun `get all seats by showtime returns empty list when no seats have been populated`() = runTest {
        val (showtimeId, _) = createShowtimeWithSeats(seatCount = 0)

        val result = repo.getAllSeatsByShowtime(showtimeId)

        assertTrue(result is DataResult.Success, result.toString())
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `get all seats by showtime returns only seats for the requested showtime`() = runTest {
        val (showtimeId1, seatIds1) = createShowtimeWithSeats(seatCount = 3)
        val (showtimeId2, seatIds2) = createShowtimeWithSeats(seatCount = 2)
        repo.populateShowtimeSeats(showtimeId1, seatIds1)
        repo.populateShowtimeSeats(showtimeId2, seatIds2)

        val result = repo.getAllSeatsByShowtime(showtimeId2)

        assertTrue(result is DataResult.Success, result.toString())
        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.showtimeId == showtimeId2 })
    }

    private suspend fun createShowtimeWithSeats(seatCount: Int): Pair<Long, List<Long>> {
        val n = Random.nextInt(100_000)
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

        if (seatCount == 0) return showtimeId to emptyList()

        val seats = (1..seatCount).map { i ->
            Seat(
                roomId = savedRoom.id,
                rowLabel = "A",
                seatNumber = i
            )
        }
        val seatIds = (seatRepo.createSeats(seats) as DataResult.Success).value.map { it.id }

        return showtimeId to seatIds
    }
}