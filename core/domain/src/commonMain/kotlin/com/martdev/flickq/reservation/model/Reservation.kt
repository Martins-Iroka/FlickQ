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
    val expiresAt: Instant = Clock.System.now()
)