package com.martdev.flickq.feature.booking.domain

import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.room.model.Seat

/** A showtime's room dimensions plus per-seat availability. */
data class SeatMap(
    val showtimeId: Long,
    val rows: Int,
    val columns: Int,
    val seatPrice: Int,
    val seats: List<SeatAvailability>
)

data class SeatAvailability(
    val seat: Seat,
    val status: SeatStatus
)
