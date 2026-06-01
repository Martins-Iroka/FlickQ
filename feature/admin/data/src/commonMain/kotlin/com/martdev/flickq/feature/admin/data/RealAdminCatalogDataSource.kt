package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.deleteForStatus
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.core.data.patchData
import com.martdev.flickq.core.data.postData
import com.martdev.flickq.core.data.postForStatus
import com.martdev.flickq.core.data.putData
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.movie.GenreDTO
import com.martdev.flickq.movie.MovieDTO
import com.martdev.flickq.movie.MovieListItemDTO
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.room.RoomDTO
import com.martdev.flickq.room.SeatDTO
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.showtime.ShowtimeDTO
import com.martdev.flickq.showtime.UpdateShowtimeStatusRequest
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import io.ktor.client.HttpClient

/**
 * Ktor-backed admin catalog. Mutations hit the ADMIN-gated `admin` routes (the Bearer
 * token is attached by the shared client's Auth plugin); lists reuse the public read
 * endpoints. The server returns 201 with no body for movie/genre create, and 204 for deletes.
 */
class RealAdminCatalogDataSource(
    private val httpClient: HttpClient
) : AdminCatalogRepository {

    // --- Movies -----------------------------------------------------------------------

    override suspend fun getMovies(limit: Int, offset: Int): Result<List<Movie>, DataError> =
        httpClient.getData<List<MovieListItemDTO>>(
            route = "/movie/get-movies",
            queryParameters = mapOf("limit" to limit, "offset" to offset),
        ).map { list -> list.map { it.toMovie() } }

    override suspend fun getMovie(id: Long): Result<Movie, DataError> =
        httpClient.getData<MovieDTO>("/movie/get-movie-by-id/$id").map { it.toMovie() }

    override suspend fun createMovie(movie: Movie): EmptyResult<DataError> =
        httpClient.postForStatus("/admin/movie/create-movie", movie.toDto())

    override suspend fun updateMovie(movie: Movie): Result<Movie, DataError> =
        httpClient.putData<MovieDTO, MovieDTO>("/admin/movie/update-movie/${movie.id}", movie.toDto())
            .map { it.toMovie() }

    override suspend fun deleteMovie(id: Long): EmptyResult<DataError> =
        httpClient.deleteForStatus("/admin/movie/delete-movie/$id")

    // --- Genres -----------------------------------------------------------------------

    override suspend fun getGenres(): Result<List<Genre>, DataError> =
        httpClient.getData<List<GenreDTO>>("/genre/genres").map { list -> list.map { it.toGenre() } }

    override suspend fun createGenre(genre: Genre): EmptyResult<DataError> =
        httpClient.postForStatus("/admin/genre/create-genre", genre.toDto())

    override suspend fun deleteGenre(id: Long): EmptyResult<DataError> =
        httpClient.deleteForStatus("/admin/genre/delete-genre/$id")

    // --- Rooms & seats ----------------------------------------------------------------

    override suspend fun getRooms(): Result<List<Room>, DataError> =
        httpClient.getData<List<RoomDTO>>("/room/get-rooms").map { list -> list.map { it.toRoom() } }

    override suspend fun createRoom(room: Room): Result<Room, DataError> =
        httpClient.postData<RoomDTO, RoomDTO>("/admin/room/create-room", room.toDto()).map { it.toRoom() }

    override suspend fun updateRoom(room: Room): Result<Room, DataError> =
        httpClient.putData<RoomDTO, RoomDTO>("/admin/room/update-room/${room.id}", room.toDto())
            .map { it.toRoom() }

    override suspend fun deleteRoom(id: Long): EmptyResult<DataError> =
        httpClient.deleteForStatus("/admin/room/delete-room/$id")

    override suspend fun createSeats(seats: List<Seat>): Result<List<Seat>, DataError> =
        httpClient.postData<List<SeatDTO>, List<SeatDTO>>("/admin/seat/create-seats", seats.map { it.toDto() })
            .map { list -> list.map { it.toSeat() } }

    // --- Showtimes --------------------------------------------------------------------

    override suspend fun getShowtimes(limit: Int, offset: Int): Result<List<Showtime>, DataError> =
        httpClient.getData<List<ShowtimeDTO>>(
            route = "/admin/showtime/get-showtimes",
            queryParameters = mapOf("limit" to limit, "offset" to offset),
        ).map { list -> list.map { it.toShowtime() } }

    override suspend fun createShowtime(showtime: Showtime): Result<Showtime, DataError> =
        httpClient.postData<ShowtimeDTO, ShowtimeDTO>("/admin/showtime/create-showtime", showtime.toDto())
            .map { it.toShowtime() }

    override suspend fun updateShowtime(showtime: Showtime): Result<Showtime, DataError> =
        httpClient.putData<ShowtimeDTO, ShowtimeDTO>("/admin/showtime/update-showtime/${showtime.id}", showtime.toDto())
            .map { it.toShowtime() }

    override suspend fun deleteShowtime(id: Long): EmptyResult<DataError> =
        httpClient.deleteForStatus("/admin/showtime/delete-showtime/$id")

    override suspend fun updateShowtimeStatus(id: Long, status: ShowtimeStatus): Result<Showtime, DataError> =
        httpClient.patchData<UpdateShowtimeStatusRequest, ShowtimeDTO>(
            route = "/admin/showtime/update-showtime-status/$id",
            body = UpdateShowtimeStatusRequest(status.name),
        ).map { it.toShowtime() }
}
