package com.martdev.flickq.feature.reservation.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket

interface MobileReservationRepository {
    suspend fun getMyReservationTickets(status: ReservationStatus?, limit: Int, offset: Int): Result<List<ReservationTicket>, DataError>
}