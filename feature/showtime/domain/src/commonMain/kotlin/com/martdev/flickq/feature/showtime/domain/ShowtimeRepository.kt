package com.martdev.flickq.feature.showtime.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.showtime.model.Showtime

interface ShowtimeRepository {
    suspend fun getShowtimesByMovieId(movieId: Long): Result<List<Showtime>, DataError>

    suspend fun getShowtimeById(id: Long): Result<Showtime, DataError>
}
