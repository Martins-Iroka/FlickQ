package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.reservation.model.ShowtimeSeat
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** In-memory reservations so the admin dashboard renders and cancel works on fakes. */
class FakeAdminReservationDataSource : AdminReservationRepository {

    private val reservations = mutableListOf(
        Reservation(
            id = 1, userId = 11, showtimeId = 1, status = ReservationStatus.CONFIRMED, totalAmount = 7000,
            seats = listOf(
                ShowtimeSeat(1, 1, 101, 1, SeatStatus.BOOKED),
                ShowtimeSeat(2, 1, 102, 1, SeatStatus.BOOKED),
            ),
            createdAt = Clock.System.now(), expiresAt = Clock.System.now() + 15.minutes,
        ),
        Reservation(
            id = 2, userId = 12, showtimeId = 2, status = ReservationStatus.PENDING, totalAmount = 3000,
            seats = listOf(ShowtimeSeat(3, 2, 201, 2, SeatStatus.HELD)),
            createdAt = Clock.System.now(), expiresAt = Clock.System.now() + 15.minutes,
        ),
    )

    override suspend fun getReservations(limit: Int, offset: Int): Result<List<Reservation>, DataError> =
        Result.Success(reservations.toList())

    override suspend fun getReservation(id: Long): Result<Reservation, DataError> =
        reservations.find { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)

    override suspend fun cancelReservation(id: Long): Result<Reservation, DataError> {
        val index = reservations.indexOfFirst { it.id == id }
        if (index < 0) return Result.Error(DataError.Network.NOT_FOUND)
        val cancelled = reservations[index].copy(status = ReservationStatus.CANCELLED)
        reservations[index] = cancelled
        return Result.Success(cancelled)
    }

    override suspend fun populateSeats(showtimeId: Long): EmptyResult<DataError> = Result.Success(Unit)
}
