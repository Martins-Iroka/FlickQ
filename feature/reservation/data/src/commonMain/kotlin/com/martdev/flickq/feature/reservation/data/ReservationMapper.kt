package com.martdev.flickq.feature.reservation.data

import com.martdev.flickq.reservation.ReservationData
import com.martdev.flickq.reservation.ReservationTicketDTO
import com.martdev.flickq.reservation.model.ReservationDomain
import com.martdev.flickq.reservation.model.ReservationPayment
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket
import kotlinx.datetime.LocalDate

fun ReservationData.toReservationDomain() = ReservationDomain(
    reservationTickets = reservations.map { it.toReservationTicketModel() },
    nextOffset
)

fun ReservationTicketDTO.toReservationTicketModel() = ReservationTicket(
    id = id,
    status = runCatching { ReservationStatus.valueOf(status) }.getOrDefault(ReservationStatus.PENDING),
    totalAmount = totalAmount,
    expiresAt = expiresAt,
    showtimeStartsAt = startsAt,
    showtimeEndsAt = endsAt,
    movieTitle = movieTitle,
    posterUrl = posterUrl,
    releasedDate = LocalDate.parse(releasedDate),
    roomName = roomName,
    seat = seats,
    payment = payment?.let {
        ReservationPayment(
            status = it.status,
            reference = it.reference,
            paidAt = it.paidAt
        )
    }
)