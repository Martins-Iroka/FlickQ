package com.martdev.flickq.features.reservation.api

import com.martdev.flickq.reservation.ReservationDTO
import com.martdev.flickq.reservation.ReservationTicketDTO
import com.martdev.flickq.reservation.ShowtimeSeatDTO
import com.martdev.flickq.reservation.TicketPaymentDTO
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationPayment
import com.martdev.flickq.reservation.model.ReservationTicket
import com.martdev.flickq.reservation.model.ShowtimeSeat

fun Reservation.toReservationDTO() = ReservationDTO(
    id = id,
    userId = userId,
    showtimeId = showtimeId,
    status = status.name,
    totalAmount = totalAmount,
    seats = seats.map { it.toShowtimeSeatDTO() },
    createdAt = createdAt,
    expiresAt = expiresAt
)

fun ShowtimeSeat.toShowtimeSeatDTO() = ShowtimeSeatDTO(
    id = id,
    showtimeId = showtimeId,
    seatId = seatId,
    reservationId = reservationId,
    status = status.name
)

fun ReservationTicket.toReservationTicketDTO() = ReservationTicketDTO(
    status = status.name,
    totalAmount = totalAmount,
    expiresAt = expiresAt,
    startsAt = showtimeStartsAt,
    endsAt = showtimeEndsAt,
    movieTitle = movieTitle,
    posterUrl = posterUrl,
    roomName = roomName,
    seats = seat,
    payment = payment?.toTicketPaymentDTO()
)

fun ReservationPayment.toTicketPaymentDTO() = TicketPaymentDTO(
    status, reference, paidAt
)