package com.martdev.flickq.feature.admin.presentation

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

/** Hand-written fakes for VM tests — the presentation module doesn't depend on `:feature:admin:data`. */
class FakeAdminCatalogRepository : AdminCatalogRepository {
    val genres = mutableListOf(Genre(1, "Drama"))
    var deleteGenreError: DataError? = null
    var createGenreCount = 0

    /** Backing catalog for pagination tests; default empty keeps non-paging tests unaffected. */
    var allMovies: List<Movie> = emptyList()
    val moviePages = mutableListOf<Pair<Int, Int>>() // (limit, offset)

    override suspend fun getMovies(limit: Int, offset: Int): Result<List<Movie>, DataError> {
        moviePages += limit to offset
        return Result.Success(allMovies.drop(offset).take(limit))
    }
    override suspend fun getMovie(id: Long): Result<Movie, DataError> = Result.Error(DataError.Network.NOT_FOUND)
    override suspend fun createMovie(movie: Movie): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun updateMovie(movie: Movie): Result<Movie, DataError> = Result.Success(movie)
    override suspend fun deleteMovie(id: Long): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun getGenres(): Result<List<Genre>, DataError> = Result.Success(genres.toList())
    override suspend fun createGenre(genre: Genre): EmptyResult<DataError> {
        createGenreCount++
        genres += genre.copy(id = 99)
        return Result.Success(Unit)
    }
    override suspend fun deleteGenre(id: Long): EmptyResult<DataError> {
        deleteGenreError?.let { return Result.Error(it) }
        genres.removeAll { it.id == id }
        return Result.Success(Unit)
    }

    override suspend fun getRooms(): Result<List<Room>, DataError> = Result.Success(emptyList())
    override suspend fun createRoom(room: Room): Result<Room, DataError> = Result.Success(room)
    override suspend fun updateRoom(room: Room): Result<Room, DataError> = Result.Success(room)
    override suspend fun deleteRoom(id: Long): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun getSeats(roomId: Long): Result<List<Seat>, DataError> = Result.Success(emptyList())
    override suspend fun createSeats(seats: List<Seat>): Result<List<Seat>, DataError> = Result.Success(seats)

    override suspend fun getShowtimes(limit: Int, offset: Int): Result<List<Showtime>, DataError> = Result.Success(emptyList())
    override suspend fun createShowtime(showtime: Showtime): Result<Showtime, DataError> = Result.Success(showtime)
    override suspend fun updateShowtime(showtime: Showtime): Result<Showtime, DataError> = Result.Success(showtime)
    override suspend fun deleteShowtime(id: Long): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun updateShowtimeStatus(id: Long, status: ShowtimeStatus): Result<Showtime, DataError> =
        Result.Success(Showtime(id = id, status = status))
}
