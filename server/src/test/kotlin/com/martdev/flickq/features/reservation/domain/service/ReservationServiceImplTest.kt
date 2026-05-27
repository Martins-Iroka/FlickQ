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
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@ExtendWith(MockKExtension::class)
class ReservationServiceImplTest {

    @MockK
    private lateinit var repo: ReservationRepository

    @MockK
    private lateinit var showtimeService: ShowtimeService

    private lateinit var service: ReservationService

    private val userId = 42L
    private val showtimeId = 7L
    private val reservationId = 1000L
    private val now = Clock.System.now()

    private val scheduledShowtime = Showtime(
        id = showtimeId,
        movieId = 1L,
        roomId = 2L,
        price = 5000,
        status = ShowtimeStatus.SCHEDULED
    )

    private val pendingReservation = Reservation(
        id = reservationId,
        userId = userId,
        showtimeId = showtimeId,
        status = ReservationStatus.PENDING,
        totalAmount = 10000,
        expiresAt = now.plus(10.minutes)
    )

    @BeforeEach
    fun setUp() {
        service = ReservationServiceImpl(repo, showtimeService)
    }

    @Test
    fun `createReservation throws BadRequest when showtime is not SCHEDULED`() = runTest {
        coEvery { showtimeService.getShowtimeById(showtimeId) } returns
                scheduledShowtime.copy(status = ShowtimeStatus.CANCELLED)

        assertThrows<BadRequestException> {
            service.createReservation(userId, showtimeId, listOf(1L, 2L))
        }
    }

    @Test
    fun `createReservation returns reservation on success`() = runTest {
        coEvery { showtimeService.getShowtimeById(showtimeId) } returns scheduledShowtime
        coEvery { repo.createReservation(any(), listOf(1L, 2L)) } returns DataResult.Success(pendingReservation)

        val result = service.createReservation(userId, showtimeId, listOf(1L, 2L))

        assertEquals(pendingReservation, result)
    }

    @Test
    fun `createReservation throws ConflictException when repo returns Conflict`() = runTest {
        coEvery { showtimeService.getShowtimeById(showtimeId) } returns scheduledShowtime
        coEvery { repo.createReservation(any(), any()) } returns DataResult.Failure.Conflict("seat unavailable")

        assertThrows<ConflictException> {
            service.createReservation(userId, showtimeId, listOf(1L, 2L))
        }
    }

    @Test
    fun `createReservation throws NotFoundException when repo returns NotFound`() = runTest {
        coEvery { showtimeService.getShowtimeById(showtimeId) } returns scheduledShowtime
        coEvery { repo.createReservation(any(), any()) } returns DataResult.Failure.NotFound("seats missing")

        assertThrows<NotFoundException> {
            service.createReservation(userId, showtimeId, listOf(1L, 2L))
        }
    }

    @Test
    fun `createReservation throws InternalServerException when repo returns UnknownError`() = runTest {
        coEvery { showtimeService.getShowtimeById(showtimeId) } returns scheduledShowtime
        coEvery { repo.createReservation(any(), any()) } returns DataResult.Failure.UnknownError("boom")

        assertThrows<InternalServerException> {
            service.createReservation(userId, showtimeId, listOf(1L, 2L))
        }
    }

    // -- getReservationById --

    @Test
    fun `getReservationById returns reservation on success`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Success(pendingReservation)

        val result = service.getReservationById(reservationId)

        assertEquals(pendingReservation, result)
    }

    @Test
    fun `getReservationById throws NotFoundException when not found`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Failure.NotFound()

        assertThrows<NotFoundException> {
            service.getReservationById(reservationId)
        }
    }

    // -- getMyReservationById --

    @Test
    fun `getMyReservationById returns reservation when caller owns it`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Success(pendingReservation)

        val result = service.getMyReservationById(reservationId, userId)

        assertEquals(pendingReservation, result)
    }

    @Test
    fun `getMyReservationById throws ForbiddenException when caller does not own the reservation`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Success(pendingReservation)

        assertThrows<ForbiddenException> {
            service.getMyReservationById(reservationId, userId = 999L)
        }
    }

    // -- getMyReservations --

    @Test
    fun `getMyReservations returns the user's reservations`() = runTest {
        val list = listOf(pendingReservation)
        coEvery { repo.getReservationsByUserId(userId) } returns DataResult.Success(list)

        val result = service.getMyReservations(userId)

        assertEquals(list, result)
    }

    // -- getAllReservations --

    @Test
    fun `getAllReservations delegates to the repository`() = runTest {
        val list = listOf(pendingReservation)
        coEvery { repo.getAllReservations(10, 0L) } returns DataResult.Success(list)

        val result = service.getAllReservations(10, 0L)

        assertEquals(list, result)
    }

    // -- confirmReservationFromPayment --

    @Test
    fun `confirmReservationFromPayment is a no-op when reservation is already CONFIRMED`() = runTest {
        val confirmed = pendingReservation.copy(status = ReservationStatus.CONFIRMED)
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Success(confirmed)

        val result = service.confirmReservationFromPayment(reservationId)

        assertEquals(confirmed, result)
    }

    @Test
    fun `confirmReservationFromPayment throws BadRequestException when reservation is CANCELLED`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns
                DataResult.Success(pendingReservation.copy(status = ReservationStatus.CANCELLED))

        assertThrows<BadRequestException> {
            service.confirmReservationFromPayment(reservationId)
        }
    }

    @Test
    fun `confirmReservationFromPayment returns the confirmed reservation on success`() = runTest {
        val confirmed = pendingReservation.copy(status = ReservationStatus.CONFIRMED)
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Success(pendingReservation)
        coEvery {
            repo.updateReservationStatus(reservationId, ReservationStatus.CONFIRMED)
        } returns DataResult.Success(confirmed)

        val result = service.confirmReservationFromPayment(reservationId)

        assertEquals(confirmed, result)
    }

    // -- cancelReservation --

    @Test
    fun `cancelReservation throws ForbiddenException when caller does not own the reservation`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Success(pendingReservation)

        assertThrows<ForbiddenException> {
            service.cancelReservation(reservationId, userId = 999L)
        }
    }

    @Test
    fun `cancelReservation throws ConflictException when reservation is already CANCELLED`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns
                DataResult.Success(pendingReservation.copy(status = ReservationStatus.CANCELLED))

        assertThrows<ConflictException> {
            service.cancelReservation(reservationId, userId)
        }
    }

    @Test
    fun `cancelReservation throws BadRequestException when reservation is CONFIRMED`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns
                DataResult.Success(pendingReservation.copy(status = ReservationStatus.CONFIRMED))

        assertThrows<BadRequestException> {
            service.cancelReservation(reservationId, userId)
        }
    }

    @Test
    fun `cancelReservation returns the cancelled reservation on success`() = runTest {
        val cancelled = pendingReservation.copy(status = ReservationStatus.CANCELLED)
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Success(pendingReservation)
        coEvery {
            repo.updateReservationStatus(reservationId, ReservationStatus.CANCELLED)
        } returns DataResult.Success(cancelled)

        val result = service.cancelReservation(reservationId, userId)

        assertEquals(cancelled, result)
    }

    // -- cancelReservationAdmin --

    @Test
    fun `cancelReservationAdmin throws ConflictException when reservation is already CANCELLED`() = runTest {
        coEvery { repo.getReservationById(reservationId) } returns
                DataResult.Success(pendingReservation.copy(status = ReservationStatus.CANCELLED))

        assertThrows<ConflictException> {
            service.cancelReservationAdmin(reservationId)
        }
    }

    @Test
    fun `cancelReservationAdmin cancels regardless of ownership`() = runTest {
        val cancelled = pendingReservation.copy(status = ReservationStatus.CANCELLED)
        // owner of pendingReservation is `userId`, but admin doesn't get a userId — no ownership check
        coEvery { repo.getReservationById(reservationId) } returns DataResult.Success(pendingReservation)
        coEvery {
            repo.updateReservationStatus(reservationId, ReservationStatus.CANCELLED)
        } returns DataResult.Success(cancelled)

        val result = service.cancelReservationAdmin(reservationId)

        assertEquals(cancelled, result)
    }

    // -- cancelExpiredReservations --

    @Test
    fun `cancelExpiredReservations delegates to the repository`() = runTest {
        coEvery { repo.cancelExpiredReservation() } returns DataResult.Success(Unit)

        service.cancelExpiredReservations()

        coVerify { repo.cancelExpiredReservation() }
    }

    @Test
    fun `cancelExpiredReservations throws InternalServerException when repository fails`() = runTest {
        coEvery { repo.cancelExpiredReservation() } returns DataResult.Failure.UnknownError("db down")

        assertThrows<InternalServerException> {
            service.cancelExpiredReservations()
        }
    }
}