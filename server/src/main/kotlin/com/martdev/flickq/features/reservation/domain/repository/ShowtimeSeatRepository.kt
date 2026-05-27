package com.martdev.flickq.features.reservation.domain.repository

import com.martdev.flickq.reservation.model.ShowtimeSeat
import com.martdev.flickq.shared.domain.model.DataResult

interface ShowtimeSeatRepository {
    suspend fun populateShowtimeSeats(showtimeId: Long, seatIds: List<Long>): DataResult<Unit>
    suspend fun getAvailableSeats(showtimeId: Long): DataResult<List<ShowtimeSeat>>
    suspend fun getAllSeatsByShowtime(showtimeId: Long): DataResult<List<ShowtimeSeat>>
}