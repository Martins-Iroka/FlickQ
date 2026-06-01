package com.martdev.flickq.feature.admin.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.reservation.model.Reservation

/**
 * Admin reservation operations over the `admin/reservation` endpoints. `cancel` returns the mutated
 * reservation; `populateSeats` seeds the showtime-seat grid for a showtime and returns no
 * payload of interest.
 */
interface AdminReservationRepository {
    suspend fun getReservations(limit: Int = 100, offset: Int = 0): Result<List<Reservation>, DataError>
    suspend fun getReservation(id: Long): Result<Reservation, DataError>
    suspend fun cancelReservation(id: Long): Result<Reservation, DataError>
    suspend fun populateSeats(showtimeId: Long): EmptyResult<DataError>
}
