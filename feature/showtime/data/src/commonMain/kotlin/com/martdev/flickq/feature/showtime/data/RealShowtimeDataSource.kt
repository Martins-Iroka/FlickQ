package com.martdev.flickq.feature.showtime.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.feature.showtime.domain.ShowtimeRepository
import com.martdev.flickq.showtime.ShowtimeDTO
import com.martdev.flickq.showtime.model.Showtime
import io.ktor.client.HttpClient

/**
 * Ktor-backed [ShowtimeRepository]. Used when [com.martdev.flickq.core.data.AppConfig.USE_FAKES]
 * is false.
 */
class RealShowtimeDataSource(
    private val client: HttpClient
) : ShowtimeRepository {

    override suspend fun getShowtimesByMovieId(movieId: Long): Result<List<Showtime>, DataError> =
        client.getData<List<ShowtimeDTO>>("/showtime/get-showtimes-by-movie-id/$movieId")
            .map { list -> list.map { it.toShowtime() } }

    override suspend fun getShowtimeById(id: Long): Result<Showtime, DataError> =
        client.getData<ShowtimeDTO>("/showtime/get-showtime-by-id/$id")
            .map { it.toShowtime() }
}
