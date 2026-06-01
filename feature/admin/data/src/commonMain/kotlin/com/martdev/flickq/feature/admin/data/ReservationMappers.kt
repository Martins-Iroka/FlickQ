package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.reservation.ReservationDTO
import com.martdev.flickq.reservation.ShowtimeSeatDTO
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.reservation.model.ShowtimeSeat

internal fun ReservationDTO.toReservation(): Reservation = Reservation(
    id = id,
    userId = userId,
    showtimeId = showtimeId,
    status = status.toReservationStatus(),
    totalAmount = totalAmount,
    seats = seats.map { it.toShowtimeSeat() },
    createdAt = createdAt,
    expiresAt = expiresAt,
)

internal fun ShowtimeSeatDTO.toShowtimeSeat(): ShowtimeSeat = ShowtimeSeat(
    id = id,
    showtimeId = showtimeId,
    seatId = seatId,
    reservationId = reservationId,
    status = status.toSeatStatus(),
)

/** Free-string status; unknown values default to PENDING so an unrecognised row is treated conservatively. */
internal fun String.toReservationStatus(): ReservationStatus =
    runCatching { ReservationStatus.valueOf(uppercase()) }.getOrDefault(ReservationStatus.PENDING)

internal fun String.toSeatStatus(): SeatStatus =
    runCatching { SeatStatus.valueOf(uppercase()) }.getOrDefault(SeatStatus.AVAILABLE)
