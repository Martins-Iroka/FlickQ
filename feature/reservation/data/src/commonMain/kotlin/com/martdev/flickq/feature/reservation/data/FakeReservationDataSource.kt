package com.martdev.flickq.feature.reservation.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.reservation.domain.MobileReservationRepository
import com.martdev.flickq.reservation.model.ReservationDomain
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class FakeReservationDataSource : MobileReservationRepository {

    private val reservations: List<ReservationTicket> = listOf(
        ReservationTicket(
            id = 1,
            totalAmount = 5000,
            expiresAt = Clock.System.now().plus(1.minutes),
            movieTitle = "The APEX",
            posterUrl = "https://www.themoviedb.org/t/p/w1280/eJGWx219ZcEMVQJhAgMiqo8tYY.jpg"
        ),
        ReservationTicket(
            id = 2,
            totalAmount = 15000,
            expiresAt = Clock.System.now().plus(5.minutes),
            movieTitle = "The APEX",
            posterUrl = "https://www.themoviedb.org/t/p/w1280/eJGWx219ZcEMVQJhAgMiqo8tYY.jpg",
            status = ReservationStatus.CONFIRMED
        )
    )
    override suspend fun getMyReservationTickets(
        status: ReservationStatus?,
        limit: Int,
        offset: Int
    ): Result<List<ReservationTicket>, DataError> {
        return Result.Success(reservations)
    }

    override suspend fun getReservationTickets(
        status: ReservationStatus?,
        limit: Int,
        offset: Int
    ): Result<ReservationDomain, DataError> {
        TODO("Not yet implemented")
    }
}