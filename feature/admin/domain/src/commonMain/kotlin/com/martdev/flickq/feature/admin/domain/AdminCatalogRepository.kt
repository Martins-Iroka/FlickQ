package com.martdev.flickq.feature.admin.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus

/**
 * Admin catalog management over the `admin/{movie,genre,room,seat,showtime}` endpoints
 * (ADMIN-gated on the server). Lists reuse the public read endpoints; mutations hit the
 * admin routes. A delete that returns 409 (the resource is still referenced) surfaces as
 * [DataError.Network.CONFLICT] for the UI to explain.
 */
interface AdminCatalogRepository {

    // --- Movies -----------------------------------------------------------------------
    /** Lightweight list (id/title/poster only); call [getMovie] for the full record. */
    suspend fun getMovies(limit: Int = 100, offset: Int = 0): Result<List<Movie>, DataError>
    suspend fun getMovie(id: Long): Result<Movie, DataError>
    suspend fun createMovie(movie: Movie): EmptyResult<DataError>
    suspend fun updateMovie(movie: Movie): Result<Movie, DataError>
    suspend fun deleteMovie(id: Long): EmptyResult<DataError>

    // --- Genres -----------------------------------------------------------------------
    suspend fun getGenres(): Result<List<Genre>, DataError>
    suspend fun createGenre(genre: Genre): EmptyResult<DataError>
    suspend fun deleteGenre(id: Long): EmptyResult<DataError>

    // --- Rooms & seats ----------------------------------------------------------------
    suspend fun getRooms(): Result<List<Room>, DataError>
    suspend fun createRoom(room: Room): Result<Room, DataError>
    suspend fun updateRoom(room: Room): Result<Room, DataError>
    suspend fun deleteRoom(id: Long): EmptyResult<DataError>
    /** Bulk-creates seats for a room (the only seat mutation the server exposes). */
    suspend fun createSeats(seats: List<Seat>): Result<List<Seat>, DataError>

    // --- Showtimes --------------------------------------------------------------------
    suspend fun getShowtimes(limit: Int = 100, offset: Int = 0): Result<List<Showtime>, DataError>
    suspend fun createShowtime(showtime: Showtime): Result<Showtime, DataError>
    suspend fun updateShowtime(showtime: Showtime): Result<Showtime, DataError>
    suspend fun deleteShowtime(id: Long): EmptyResult<DataError>
    suspend fun updateShowtimeStatus(id: Long, status: ShowtimeStatus): Result<Showtime, DataError>
}
