package com.martdev.flickq.features.reservation.api

import com.martdev.flickq.reservation.ReservationDTO
import com.martdev.flickq.reservation.ShowtimeSeatDTO
import com.martdev.flickq.reservation.model.Reservation
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