package com.martdev.flickq.features.reservation.domain.service

import com.martdev.flickq.features.reservation.domain.repository.ShowtimeSeatRepository
import com.martdev.flickq.features.room.domain.service.SeatService
import com.martdev.flickq.features.showtime.domain.service.ShowtimeService
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.reservation.model.ShowtimeSeat
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.showtime.model.Showtime
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

@ExtendWith(MockKExtension::class)
class ShowtimeSeatServiceImplTest {

    @MockK
    private lateinit var repo: ShowtimeSeatRepository

    @MockK
    private lateinit var showtimeService: ShowtimeService

    @MockK
    private lateinit var seatService: SeatService

    private lateinit var service: ShowtimeSeatService

    private val showtime = Showtime(id = 1L, movieId = 1L, roomId = 5L)
    private val seats = listOf(
        Seat(id = 10L, roomId = 5L, rowLabel = "A", seatNumber = 1),
        Seat(id = 11L, roomId = 5L, rowLabel = "A", seatNumber = 2),
    )
    private val showtimeSeats = listOf(
        ShowtimeSeat(id = 100L, showtimeId = 1L, seatId = 10L, status = SeatStatus.AVAILABLE),
        ShowtimeSeat(id = 101L, showtimeId = 1L, seatId = 11L, status = SeatStatus.HELD),
    )

    @BeforeEach
    fun setUp() {
        service = ShowtimeSeatServiceImpl(repo, showtimeService, seatService)
    }

    @Test
    fun `populateShowtimeSeats resolves seats for the showtime's room and calls the repository`() = runTest {
        coEvery { showtimeService.getShowtimeById(1L) } returns showtime
        coEvery { seatService.getSeatsByRoomId(5L) } returns seats
        coEvery { repo.populateShowtimeSeats(1L, listOf(10L, 11L)) } returns DataResult.Success(Unit)

        service.populateShowtimeSeats(1L)

        coVerify {
            showtimeService.getShowtimeById(1L)
            seatService.getSeatsByRoomId(5L)
            repo.populateShowtimeSeats(1L, listOf(10L, 11L))
        }
    }

    @Test
    fun `populateShowtimeSeats throws InternalServerException when repository returns UnknownError`() = runTest {
        coEvery { showtimeService.getShowtimeById(1L) } returns showtime
        coEvery { seatService.getSeatsByRoomId(5L) } returns seats
        coEvery {
            repo.populateShowtimeSeats(1L, listOf(10L, 11L))
        } returns DataResult.Failure.UnknownError("boom")

        assertThrows<InternalServerException> {
            service.populateShowtimeSeats(1L)
        }
    }

    @Test
    fun `getAvailableSeats returns the seats from the repository`() = runTest {
        coEvery { repo.getAvailableSeats(1L) } returns DataResult.Success(showtimeSeats)

        val result = service.getAvailableSeats(1L)

        assertEquals(showtimeSeats, result)
    }

    @Test
    fun `getAvailableSeats throws NotFoundException when repository returns NotFound`() = runTest {
        coEvery { repo.getAvailableSeats(1L) } returns DataResult.Failure.NotFound("no seats")

        assertThrows<NotFoundException> {
            service.getAvailableSeats(1L)
        }
    }

    @Test
    fun `getAllSeatsByShowtime returns the seats from the repository`() = runTest {
        coEvery { repo.getAllSeatsByShowtime(1L) } returns DataResult.Success(showtimeSeats)

        val result = service.getAllSeatsByShowtime(1L)

        assertEquals(showtimeSeats, result)
    }

    @Test
    fun `getAllSeatsByShowtime throws InternalServerException when repository returns UnknownError`() = runTest {
        coEvery { repo.getAllSeatsByShowtime(1L) } returns DataResult.Failure.UnknownError("db down")

        assertThrows<InternalServerException> {
            service.getAllSeatsByShowtime(1L)
        }
    }
}
