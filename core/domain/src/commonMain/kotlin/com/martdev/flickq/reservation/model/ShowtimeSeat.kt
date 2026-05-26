package com.martdev.flickq.reservation.model

data class ShowtimeSeat(
    val id: Long = 0,
    val showtimeId: Long = 0,
    val seatId: Long = 0,
    val reservationId: Long? = null,
    val status: SeatStatus = SeatStatus.AVAILABLE
)