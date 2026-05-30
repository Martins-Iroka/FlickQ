package com.martdev.flickq.feature.booking.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.reservation.model.Reservation

interface BookingRepository {
    suspend fun getSeatMap(showtimeId: Long): Result<SeatMap, DataError>

    /** Mirrors the backend's CreateReservationRequest: { showtime_id, seat_ids }. */
    suspend fun createReservation(showtimeId: Long, seatIds: List<Long>): Result<Reservation, DataError>

    suspend fun getReservation(id: Long): Result<Reservation, DataError>
}
