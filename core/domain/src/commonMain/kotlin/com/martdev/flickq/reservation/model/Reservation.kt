package com.martdev.flickq.reservation.model

import kotlin.time.Clock
import kotlin.time.Instant

data class Reservation(
    val id: Long = 0,
    val userId: Long = 0,
    val showtimeId: Long = 0,
    val status: ReservationStatus = ReservationStatus.PENDING,
    val totalAmount: Long = 0,
    val seats: List<ShowtimeSeat> = emptyList(),
    val createdAt: Instant = Clock.System.now(),
    val expiresAt: Instant = Clock.System.now(),
    val movieId: Long = 0,
    val movieTitle: String = "",
    val posterUrl: String = "",
    val roomId: Long = 0,
    val roomName: String = "",
    val showtimeStartsAt: Instant = Clock.System.now(),
    val showtimeEndsAt: Instant = Clock.System.now(),
    val reservedSeats: List<ReservationSeat> = emptyList(),
    val payment: ReservationPayment? = null
)

data class ReservationSeat(
    val seatId: Long = 0,
    val seat: String = ""
)

data class ReservationPayment(
    val status: String = "",
    val reference: String = "",
    val paidAt: Instant? = null
)