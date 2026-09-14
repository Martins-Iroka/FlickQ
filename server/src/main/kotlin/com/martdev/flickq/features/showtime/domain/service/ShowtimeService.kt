package com.martdev.flickq.features.showtime.domain.service

import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.datetime.LocalDate

interface ShowtimeService {
    suspend fun createShowtime(showtime: Showtime): Showtime
    suspend fun getShowtimes(limit: Int, offset: Long): List<Showtime>
    suspend fun getShowtimesByMovieId(movieId: Long): List<Showtime>
    suspend fun getShowtimeById(id: Long): Showtime
    suspend fun updateShowtime(showtime: Showtime): Showtime
    suspend fun deleteShowtime(id: Long)
    suspend fun updateShowtimeStatus(id: Long, status: ShowtimeStatus): Showtime
    suspend fun getShowtimeMovieIds(date: LocalDate): List<Long>
}