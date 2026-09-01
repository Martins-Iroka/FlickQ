package com.martdev.flickq.feature.reservation.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.feature.reservation.domain.MobileReservationRepository
import com.martdev.flickq.reservation.ReservationTicketDTO
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket
import io.ktor.client.HttpClient

class MobileReservationRepoImpl(
    private val client: HttpClient
) : MobileReservationRepository {
    override suspend fun getMyReservationTickets(
        status: ReservationStatus,
        limit: Int,
        offset: Int
    ): Result<List<ReservationTicket>, DataError> {
       return client.getData<List<ReservationTicketDTO>>(
            "/reservation/my-reservations",
            queryParameters = mapOf("status" to status.toString(), "limit" to limit, "offset" to offset)
        ).map { items -> items.map {
            it.toReservationTicketModel()
        } }
    }
}