package com.martdev.flickq.feature.reservation.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.reservation.domain.MobileReservationRepository
import com.martdev.flickq.reservation.model.ReservationDomain
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.reservation.model.ReservationTicket
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    private val r = (1..20).map {
        ReservationTicket(
            id = it.toLong(),
            totalAmount = (2 * it).toLong(),
            movieTitle = "The APEX",
            posterUrl = "https://www.themoviedb.org/t/p/w1280/eJGWx219ZcEMVQJhAgMiqo8tYY.jpg",
            status = if (it % 2 == 0) ReservationStatus.CONFIRMED else ReservationStatus.PENDING
        )
    }
    var isRetried = false
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
        println("The status is $status")
        delay(1.seconds)
        return if (isRetried) {
            if (offset != 0) {
                Result.Error(error = DataError.Network.SERVER_ERROR, "Server error")
            } else {
                val t = r.take(limit)
                val tt = if (status == null) t else t.filter { it.status == status }
                Result.Success(ReservationDomain(tt, 5))
            }
        } else {
            isRetried = true
            Result.Error(DataError.Network.SERVER_ERROR, "Server error")
        }
    }
}