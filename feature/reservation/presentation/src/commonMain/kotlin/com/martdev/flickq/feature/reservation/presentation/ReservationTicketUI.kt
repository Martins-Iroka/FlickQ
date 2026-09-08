package com.martdev.flickq.feature.reservation.presentation

import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

data class ReservationTicketUI(
    val id: Long,
    val status: ReservationStatus,
    val totalAmount: Long = 0,
    val expiresAt: Instant = Clock.System.now(),
    val showtimeStartsAt: Instant = Clock.System.now(),
    val showtimeEndsAt: Instant = Clock.System.now(),
    val movieTitle: String = "",
    val posterUrl: String = "",
    val releasedDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
    val roomName: String = "",
    val seat: String = "",
)

fun ReservationTicket.toReservationTicketUI() = ReservationTicketUI(
    id,
    status,
    totalAmount,
    expiresAt,
    showtimeStartsAt,
    showtimeEndsAt,
    movieTitle,
    posterUrl,
    releasedDate,
    roomName,
    seat
)