package com.martdev.flickq.features.reservation.domain.service

import com.martdev.flickq.reservation.model.ShowtimeSeat

interface ShowtimeSeatService {
    suspend fun populateShowtimeSeats(showtimeId: Long)
    suspend fun getAvailableSeats(showtimeId: Long): List<ShowtimeSeat>
    suspend fun getAllSeatsByShowtime(showtimeId: Long): List<ShowtimeSeat>
}