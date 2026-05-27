package com.martdev.flickq.features.showtime.infrastructure.db.repository

import com.martdev.flickq.features.movie.domain.repository.GenreRepository
import com.martdev.flickq.features.movie.domain.repository.MovieRepository
import com.martdev.flickq.features.movie.infrasturcture.repository.GenreRepositoryImpl
import com.martdev.flickq.features.movie.infrasturcture.repository.MovieRepositoryImpl
import com.martdev.flickq.features.movie.infrasturcture.tables.GenresTable
import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.room.domain.repository.RoomRepository
import com.martdev.flickq.features.room.infrastructure.db.repository.RoomRepositoryImpl
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeTable
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Testcontainers
class ShowtimeRepositoryImplTest {

    companion object {
        private lateinit var repo: ShowtimeRepository
        private lateinit var movieRepo: MovieRepository
        private lateinit var roomRepo: RoomRepository
        private lateinit var genreRepo: GenreRepository
        private val clock = Clock.System.now()

        @Container
        private val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connectDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            repo = ShowtimeRepositoryImpl()
            movieRepo = MovieRepositoryImpl()
            roomRepo = RoomRepositoryImpl()
            genreRepo = GenreRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDb() {
        transaction {
            ShowtimeTable.deleteAll()
            RoomTable.deleteAll()
            MoviesTable.deleteAll()
            GenresTable.deleteAll()
        }
    }

    @Test
    fun `create show time successfully`() = runTest {
        val (movieId, roomId) = createMovieAndRoom()

        val showtime = Showtime(
            movieId = movieId,
            roomId = roomId,
            startsAt = clock.minus(2.hours),
            endsAt = clock,
            price = 5000
        )
        val createdShowtime = repo.createShowtime(showtime)

        assertTrue(createdShowtime is DataResult.Success, createdShowtime.toString())
        assertEquals(showtime.roomId, createdShowtime.value.roomId)
        assertEquals(showtime.movieId, createdShowtime.value.movieId)
    }

    @Test
    fun `get show times successfully`() = runTest {
        val (movieId, roomId) = createMovieAndRoom()
        val showtime = Showtime(
            movieId = movieId,
            roomId = roomId,
            startsAt = clock.minus(2.hours),
            endsAt = clock,
            price = 5000
        )
        repo.createShowtime(showtime)

        val (movieId2, roomId2) = createMovieAndRoom("APEX", "Thriller")
        val showtime2 = Showtime(
            movieId = movieId2,
            roomId = roomId2,
            startsAt = clock.plus(1.hours),
            endsAt = clock.plus(3.hours),
            price = 5000
        )
        repo.createShowtime(showtime2)

        val showTimesResult = repo.getShowtimes(2, 0)

        assertTrue(showTimesResult is DataResult.Success)
        assertEquals(2, showTimesResult.value.size)
    }

    @Test
    fun `get show time by id`() = runTest {
        val (movieId, roomId) = createMovieAndRoom()
        val showtime = Showtime(
            movieId = movieId,
            roomId = roomId,
            startsAt = clock,
            endsAt = clock.plus(1.hours),
            price = 5000
        )
        val savedShowTime = (repo.createShowtime(showtime) as DataResult.Success).value

        val retrievedShowtime = repo.getShowtimeById(savedShowTime.id)
        assertTrue(retrievedShowtime is DataResult.Success)
        assertEquals(savedShowTime.id, retrievedShowtime.value.id)
    }

    @Test
    fun `get show times by movieId`() = runTest {
        val (movieId, roomId) = createMovieAndRoom("HULK")
        val showtime = Showtime(
            movieId = movieId,
            roomId = roomId,
            startsAt = clock,
            endsAt = clock.plus(1.hours),
            price = 5000
        )

        val room = Room(name = "Room ${Random.nextInt(10)}", rows = 5, columns = 10)

        val roomId2 = (roomRepo.createRoom(room) as DataResult.Success).value.id

        val showtime2 = Showtime(
            movieId = movieId,
            roomId = roomId2,
            startsAt = clock.plus(2.hours),
            endsAt = clock.plus(3.hours),
            price = 5000
        )

        val (movieId2, roomId3) = createMovieAndRoom("APEX", "Thriller")
        val showtime3 = Showtime(
            movieId = movieId2,
            roomId = roomId3,
            startsAt = clock,
            endsAt = clock.plus(2.hours),
            price = 3000
        )

        repo.createShowtime(showtime)
        repo.createShowtime(showtime2)
        repo.createShowtime(showtime3)

        val result = repo.getShowtimesByMovieId(movieId)
        assertTrue(result is DataResult.Success)
        assertEquals(2, result.value.size)
    }

    @Test
    fun `update show time`() = runTest {
        val (movieId, roomId) = createMovieAndRoom("X-MEN")
        var showtime = Showtime(
            movieId = movieId,
            roomId = roomId,
            startsAt = clock,
            endsAt = clock.plus(1.hours),
            price = 5000
        )
        val savedShowtime = (repo.createShowtime(showtime) as DataResult.Success).value

        showtime = savedShowtime.copy(price = 6000)

        val updatedShowtimeResult = repo.updateShowtime(showtime)
        assertTrue(updatedShowtimeResult is DataResult.Success)
        assertEquals(savedShowtime.id, updatedShowtimeResult.value.id)
        assertNotEquals(savedShowtime.price, updatedShowtimeResult.value.price)
    }

    @Test
    fun `delete show time`() = runTest {
        val showtime = createMovieRoomAndShowtime()

        val deleteRowResult = repo.deleteShowtime(showtime.id)
        assertTrue(deleteRowResult is DataResult.Success)
        assertEquals(1, deleteRowResult.value)
    }

    @Test
    fun `update show time status`() = runTest {
        val showtime = createMovieRoomAndShowtime()

        val result = repo.updateShowtimeStatus(showtime.id, ShowtimeStatus.COMPLETED)
        assertTrue(result is DataResult.Success)
        assertNotEquals(showtime.status, result.value.status)
        assertEquals(ShowtimeStatus.COMPLETED, result.value.status)
    }

    @Test
    fun `has overlapping showtime`() = runTest {
        val startDate = LocalDateTime(year = 2026, month = 5, day = 18, hour = 12, minute = 37).toInstant(
            TimeZone.UTC)
        val endDate = LocalDateTime(year = 2026, month = 5, day = 18, hour = 14, minute = 37).toInstant(TimeZone.UTC)
        val (movieId, roomId) = createMovieAndRoom("APEX", "Adventure")
        val (movieId2, _) = createMovieAndRoom("X-MEN")
        val showtime = Showtime(
            movieId = movieId,
            roomId = roomId,
            startsAt = startDate,
            endsAt = endDate
        )
        repo.createShowtime(showtime)

        val result = repo.createShowtime(
            showtime.copy(
                movieId = movieId2,
                startsAt = startDate.plus(1.hours),
                endsAt = endDate.plus(3.hours)
            )
        )

        assertTrue(result is DataResult.Failure.Conflict)
    }

    private suspend fun createMovieAndRoom(
        movieTitle: String = "HULK",
        genreTitle: String = "Action"
    ): Pair<Long, Long> {
        val genre = Genre(name = genreTitle)
        val savedGenre = genreRepo.saveGenre(genre) as DataResult.Success
        val movie = Movie(
            title = movieTitle,
            description = "$movieTitle description",
            posterUrl = "poster_url",
            genres = listOf(
                savedGenre.value
            )
        )

        val movieId = (movieRepo.createMovie(movie) as DataResult.Success).value

        val room = Room(name = "Room ${Random.nextInt(10)}", rows = 5, columns = 10)

        val roomId = (roomRepo.createRoom(room) as DataResult.Success).value.id

        return movieId to roomId
    }

    private suspend fun createMovieRoomAndShowtime(
        movieTitle: String = "Hulk",
        startAt: Instant = clock,
        endAt: Instant = clock.plus(1.hours)
    ): Showtime {
        val (movieId, roomId) = createMovieAndRoom(movieTitle)
        val showtime = Showtime(
            movieId = movieId,
            roomId = roomId,
            startsAt = startAt,
            endsAt = endAt,
            price = 5000
        )
        return (repo.createShowtime(showtime) as DataResult.Success).value
    }
}