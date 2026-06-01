package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * In-memory, stateful catalog so the admin dashboard is fully usable on fakes — creates,
 * edits and deletes persist for the session. Swapped for [RealAdminCatalogDataSource] when
 * [com.martdev.flickq.core.data.AppConfig.USE_FAKES] is false.
 */
class FakeAdminCatalogDataSource : AdminCatalogRepository {

    private var nextId = 100L
    private fun id() = nextId++

    private val genres = mutableListOf(
        Genre(1, "Drama"), Genre(2, "Sci-Fi"), Genre(3, "Thriller"), Genre(4, "Comedy"),
    )
    private val movies = mutableListOf(
        Movie(1, "Neon Skyline", "A neon-soaked heist.", "", 128, LocalDate(2026, 3, 14), listOf(genres[1], genres[2])),
        Movie(2, "The Quiet Coast", "A slow-burning drama.", "", 102, LocalDate(2026, 1, 9), listOf(genres[0])),
        Movie(4, "Last Call at the Atlas", "One night, one bar.", "", 96, LocalDate(2026, 5, 2), listOf(genres[0], genres[3])),
    )
    private val rooms = mutableListOf(
        Room(1, "Screen 1", 8, 10), Room(2, "Screen 2", 8, 10), Room(3, "Screen 3", 6, 10),
    )
    private val showtimes = mutableListOf(
        Showtime(1, 1, 1, now(), now() + 2.hours, 3500, ShowtimeStatus.SCHEDULED),
        Showtime(2, 2, 2, now() + 1.days, now() + 1.days + 2.hours, 3000, ShowtimeStatus.SCHEDULED),
        Showtime(3, 4, 3, now() + 2.days, now() + 2.days + 2.hours, 4000, ShowtimeStatus.CANCELLED),
    )

    private fun now() = Clock.System.now()

    override suspend fun getMovies(limit: Int, offset: Int): Result<List<Movie>, DataError> =
        Result.Success(movies.map { Movie(it.id, it.title, posterUrl = it.posterUrl) })

    override suspend fun getMovie(id: Long): Result<Movie, DataError> =
        movies.find { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)

    override suspend fun createMovie(movie: Movie): EmptyResult<DataError> {
        movies += movie.copy(id = id())
        return Result.Success(Unit)
    }

    override suspend fun updateMovie(movie: Movie): Result<Movie, DataError> {
        val index = movies.indexOfFirst { it.id == movie.id }
        if (index < 0) return Result.Error(DataError.Network.NOT_FOUND)
        movies[index] = movie
        return Result.Success(movie)
    }

    override suspend fun deleteMovie(id: Long): EmptyResult<DataError> {
        movies.removeAll { it.id == id }
        return Result.Success(Unit)
    }

    override suspend fun getGenres(): Result<List<Genre>, DataError> = Result.Success(genres.toList())

    override suspend fun createGenre(genre: Genre): EmptyResult<DataError> {
        genres += genre.copy(id = id())
        return Result.Success(Unit)
    }

    override suspend fun deleteGenre(id: Long): EmptyResult<DataError> {
        genres.removeAll { it.id == id }
        return Result.Success(Unit)
    }

    override suspend fun getRooms(): Result<List<Room>, DataError> = Result.Success(rooms.toList())

    override suspend fun createRoom(room: Room): Result<Room, DataError> {
        val created = room.copy(id = id())
        rooms += created
        return Result.Success(created)
    }

    override suspend fun updateRoom(room: Room): Result<Room, DataError> {
        val index = rooms.indexOfFirst { it.id == room.id }
        if (index < 0) return Result.Error(DataError.Network.NOT_FOUND)
        rooms[index] = room
        return Result.Success(room)
    }

    override suspend fun deleteRoom(id: Long): EmptyResult<DataError> {
        rooms.removeAll { it.id == id }
        return Result.Success(Unit)
    }

    override suspend fun createSeats(seats: List<Seat>): Result<List<Seat>, DataError> =
        Result.Success(seats.map { it.copy(id = id()) })

    override suspend fun getShowtimes(limit: Int, offset: Int): Result<List<Showtime>, DataError> =
        Result.Success(showtimes.toList())

    override suspend fun createShowtime(showtime: Showtime): Result<Showtime, DataError> {
        val created = showtime.copy(id = id())
        showtimes += created
        return Result.Success(created)
    }

    override suspend fun updateShowtime(showtime: Showtime): Result<Showtime, DataError> {
        val index = showtimes.indexOfFirst { it.id == showtime.id }
        if (index < 0) return Result.Error(DataError.Network.NOT_FOUND)
        showtimes[index] = showtime
        return Result.Success(showtime)
    }

    override suspend fun deleteShowtime(id: Long): EmptyResult<DataError> {
        showtimes.removeAll { it.id == id }
        return Result.Success(Unit)
    }

    override suspend fun updateShowtimeStatus(id: Long, status: ShowtimeStatus): Result<Showtime, DataError> {
        val index = showtimes.indexOfFirst { it.id == id }
        if (index < 0) return Result.Error(DataError.Network.NOT_FOUND)
        val updated = showtimes[index].copy(status = status)
        showtimes[index] = updated
        return Result.Success(updated)
    }
}
