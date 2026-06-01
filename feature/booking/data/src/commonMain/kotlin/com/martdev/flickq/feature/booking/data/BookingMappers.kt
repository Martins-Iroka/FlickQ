package com.martdev.flickq.feature.booking.data

import com.martdev.flickq.reservation.ReservationDTO
import com.martdev.flickq.reservation.ShowtimeSeatDTO
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.reservation.model.ShowtimeSeat
import com.martdev.flickq.room.SeatDTO
import com.martdev.flickq.room.model.Seat

internal fun SeatDTO.toSeat(): Seat = Seat(
    id = id,
    roomId = roomId,
    rowLabel = rowLabel,
    seatNumber = seatNumber
)

internal fun ShowtimeSeatDTO.toShowtimeSeat(): ShowtimeSeat = ShowtimeSeat(
    id = id,
    showtimeId = showtimeId,
    seatId = seatId,
    reservationId = reservationId,
    status = status.toSeatStatus()
)

internal fun ReservationDTO.toReservation(): Reservation = Reservation(
    id = id,
    userId = userId,
    showtimeId = showtimeId,
    status = status.toReservationStatus(),
    totalAmount = totalAmount,
    seats = seats.map { it.toShowtimeSeat() },
    createdAt = createdAt,
    expiresAt = expiresAt
)

/** Unknown seat states map to [SeatStatus.BOOKED] so a seat is never wrongly offered. */
internal fun String.toSeatStatus(): SeatStatus =
    runCatching { SeatStatus.valueOf(uppercase()) }.getOrDefault(SeatStatus.BOOKED)

internal fun String.toReservationStatus(): ReservationStatus =
    runCatching { ReservationStatus.valueOf(uppercase()) }.getOrDefault(ReservationStatus.PENDING)
