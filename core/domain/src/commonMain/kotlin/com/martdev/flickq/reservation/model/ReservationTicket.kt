package com.martdev.flickq.reservation.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

data class ReservationTicket(
    val status: ReservationStatus = ReservationStatus.PENDING,
    val totalAmount: Long = 0,
    val expiresAt: Instant = Clock.System.now(),
    val showtimeStartsAt: Instant = Clock.System.now(),
    val showtimeEndsAt: Instant = Clock.System.now(),
    val movieTitle: String = "",
    val posterUrl: String = "",
    val releasedDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
    val roomName: String = "",
    val seat: String = "",
    val payment: ReservationPayment? = null
)
