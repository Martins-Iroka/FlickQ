package com.martdev.flickq.features.reservation.domain.service

import com.martdev.flickq.features.reservation.domain.repository.ReservationRepository
import com.martdev.flickq.features.showtime.domain.service.ShowtimeService
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ReservationStatus
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.ForbiddenException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.util.returnValue
import com.martdev.flickq.showtime.model.ShowtimeStatus
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// implement reservation events and check unit test is fine
@Single
class ReservationServiceImpl(
    private val repo: ReservationRepository,
    private val showtimeService: ShowtimeService
) : ReservationService {
    override suspend fun createReservation(
        userId: Long,
        showtimeId: Long,
        seatIds: List<Long>
    ): Reservation {

        val showtime = showtimeService.getShowtimeById(showtimeId) // throws NotfoundException if missing
        if (showtime.status != ShowtimeStatus.SCHEDULED) {
            throw BadRequestException("Showtime is not available for booking")
        }

        val totalAmount = showtime.price.toLong() * seatIds.size
        val reservation = Reservation(
            userId = userId,
            showtimeId = showtimeId,
            totalAmount = totalAmount,
            expiresAt = Clock.System.now().plus(15.minutes)
        )

        return when (val result = repo.createReservation(reservation, seatIds)) {
            is DataResult.Success -> result.value
            is DataResult.Failure.Conflict -> throw ConflictException(result.errorMessage)
            is DataResult.Failure.NotFound -> throw NotFoundException(result.errorMessage)
            else -> throw InternalServerException()
        }
    }

    override suspend fun getReservationById(id: Long): Reservation {
        return repo.getReservationById(id).returnValue()
    }

    override suspend fun getMyReservationById(
        id: Long,
        userId: Long
    ): Reservation {
        val reservation = repo.getReservationById(id).returnValue()
        if (reservation.userId != userId) throw ForbiddenException()
        return reservation
    }

    override suspend fun getMyReservations(userId: Long): List<Reservation> {
        return repo.getReservationsByUserId(userId).returnValue()
    }

    override suspend fun getAllReservations(
        limit: Int,
        offset: Long
    ): List<Reservation> {
        return repo.getAllReservations(limit, offset).returnValue()
    }

    override suspend fun confirmReservationFromPayment(id: Long): Reservation {
        val reservation = repo.getReservationById(id).returnValue()
        if (reservation.status == ReservationStatus.CONFIRMED) return reservation
        if (reservation.status == ReservationStatus.CANCELLED) throw BadRequestException("Cannot confirm a cancelled reservation")

        return repo.updateReservationStatus(id, ReservationStatus.CONFIRMED).returnValue()
    }

    override suspend fun cancelReservation(
        id: Long,
        userId: Long
    ): Reservation {
        val reservation = repo.getReservationById(id).returnValue()
        if (reservation.userId != userId) throw ForbiddenException()
        if (reservation.status == ReservationStatus.CANCELLED) throw ConflictException("Reservation is already cancelled")
        if (reservation.status == ReservationStatus.CONFIRMED) {
            throw BadRequestException("Contact support to cancel a confirmed reservation")
        }

        return repo.updateReservationStatus(id, ReservationStatus.CANCELLED).returnValue()
    }

    override suspend fun cancelReservationAdmin(id: Long): Reservation {
        val reservation = repo.getReservationById(id).returnValue()
        if (reservation.status == ReservationStatus.CANCELLED) throw ConflictException("Reservation is already cancelled")

        return repo.updateReservationStatus(id, ReservationStatus.CANCELLED).returnValue()
    }

    override suspend fun cancelExpiredReservations() {
        repo.cancelExpiredReservation().returnValue()
    }
}