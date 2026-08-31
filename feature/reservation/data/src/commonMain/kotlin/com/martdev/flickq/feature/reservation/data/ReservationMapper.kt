package com.martdev.flickq.feature.reservation.data

import com.martdev.flickq.reservation.ReservationTicketDTO
import com.martdev.flickq.reservation.model.ReservationPayment
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket
import kotlinx.datetime.LocalDate

fun ReservationTicketDTO.toReservationTicketModel() = ReservationTicket(
    status = runCatching { ReservationStatus.valueOf(status) }.getOrDefault(ReservationStatus.PENDING),
    totalAmount = totalAmount,
    expiresAt = expiresAt,
    showtimeEndsAt = startsAt,
    showtimeStartsAt = endsAt,
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