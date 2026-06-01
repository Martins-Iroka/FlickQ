package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.core.data.patchData
import com.martdev.flickq.core.data.postForStatusNoBody
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import com.martdev.flickq.reservation.ReservationDTO
import com.martdev.flickq.reservation.model.Reservation
import io.ktor.client.HttpClient

/** Ktor-backed admin reservation operations over the `admin/reservation` endpoints. */
class RealAdminReservationDataSource(
    private val httpClient: HttpClient
) : AdminReservationRepository {

    override suspend fun getReservations(limit: Int, offset: Int): Result<List<Reservation>, DataError> =
        httpClient.getData<List<ReservationDTO>>(
            route = "/admin/reservation/get-all",
            queryParameters = mapOf("limit" to limit, "offset" to offset),
        ).map { list -> list.map { it.toReservation() } }

    override suspend fun getReservation(id: Long): Result<Reservation, DataError> =
        httpClient.getData<ReservationDTO>("/admin/reservation/get-by-id/$id").map { it.toReservation() }

    override suspend fun cancelReservation(id: Long): Result<Reservation, DataError> =
        httpClient.patchData<Unit, ReservationDTO>("/admin/reservation/cancel/$id").map { it.toReservation() }

    override suspend fun populateSeats(showtimeId: Long): EmptyResult<DataError> =
        httpClient.postForStatusNoBody("/admin/reservation/populate-seats/$showtimeId")
}
