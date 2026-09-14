package com.martdev.flickq.feature.reservation.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.feature.reservation.domain.MobileReservationRepository
import com.martdev.flickq.reservation.ReservationData
import com.martdev.flickq.reservation.ReservationTicketDTO
import com.martdev.flickq.reservation.model.ReservationDomain
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket
import io.ktor.client.HttpClient

class MobileReservationRepoImpl(
    private val client: HttpClient
) : MobileReservationRepository {
    override suspend fun getMyReservationTickets(
        status: ReservationStatus?,
        limit: Int,
        offset: Int
    ): Result<List<ReservationTicket>, DataError> {
        val query = mutableMapOf<String, Any>("limit" to limit, "offset" to offset)
        if (status != null) {
            query["status"] = status.toString()
        }
       return client.getData<List<ReservationTicketDTO>>(
            "/reservation/my-reservations",
            queryParameters = query
        ).map { items -> items.map {
            it.toReservationTicketModel()
        } }
    }

    override suspend fun getReservationTickets(
        status: ReservationStatus?,
        limit: Int,
        offset: Int
    ): Result<ReservationDomain, DataError> {
        val query = mutableMapOf<String, Any>("limit" to limit, "offset" to offset)
        if (status != null) {
            query["status"] = status.toString()
        }
        return client.getData<ReservationData>(
            "/reservation/my-reservations",
            queryParameters = query
        ).map { reservationData ->
            reservationData.toReservationDomain()
        }
    }
}