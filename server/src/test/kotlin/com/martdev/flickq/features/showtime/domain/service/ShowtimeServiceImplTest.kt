package com.martdev.flickq.features.showtime.domain.service

import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.shared.domain.exception.ConflictException
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

@ExtendWith(MockKExtension::class)
class ShowtimeServiceImplTest {
    @MockK
    private lateinit var repo: ShowtimeRepository
    private lateinit var service: ShowtimeService

    private val sampleShowtime = Showtime(id = 1, movieId = 1, roomId = 1)

    @BeforeEach
    fun setUp() {
        service = ShowtimeServiceImpl(repo)
    }

    @Test
    fun `createShowtime should return showtime on success`() = runTest {
        coEvery { repo.createShowtime(any()) } returns DataResult.Success(sampleShowtime)

        val result = service.createShowtime(sampleShowtime)

        assertEquals(sampleShowtime, result)
    }

    @Test
    fun `createShowtime should throw ConflictException when repository returns conflict`() = runTest {
        coEvery { repo.createShowtime(any()) } returns DataResult.Failure.Conflict("Room is already booked")

        assertThrows<ConflictException> {
            service.createShowtime(sampleShowtime)
        }
    }

    @Test
    fun `createShowtime should throw NotFoundException when repository returns ForeignKeyViolation`() = runTest {
        coEvery { repo.createShowtime(any()) } returns DataResult.Failure.ForeignKeyViolation

        assertThrows<NotFoundException> {
            service.createShowtime(sampleShowtime)
        }
    }

    @Test
    fun `getShowtimes should return list of showtimes`() = runTest {
        val showtimes = listOf(sampleShowtime)
        coEvery { repo.getShowtimes(any(), any()) } returns DataResult.Success(showtimes)

        val result = service.getShowtimes(10, 0)

        assertEquals(showtimes, result)
    }

    @Test
    fun `getShowtimesByMovieId should return list of showtimes`() = runTest {
        val showtimes = listOf(sampleShowtime)
        coEvery { repo.getShowtimesByMovieId(1L) } returns DataResult.Success(showtimes)

        val result = service.getShowtimesByMovieId(1L)

        assertEquals(showtimes, result)
    }

    @Test
    fun `getShowtimeById should return showtime on success`() = runTest {
        coEvery { repo.getShowtimeById(1L) } returns DataResult.Success(sampleShowtime)

        val result = service.getShowtimeById(1L)

        assertEquals(sampleShowtime, result)
    }

    @Test
    fun `updateShowtime should return updated showtime on success`() = runTest {
        coEvery { repo.updateShowtime(any()) } returns DataResult.Success(sampleShowtime)

        val result = service.updateShowtime(sampleShowtime)

        assertEquals(sampleShowtime, result)
    }

    @Test
    fun `updateShowtime should throw NotFoundException when showtime not found`() = runTest {
        coEvery { repo.updateShowtime(any()) } returns DataResult.Failure.NotFound()

        assertThrows<NotFoundException> {
            service.updateShowtime(sampleShowtime)
        }
    }

    @Test
    fun `deleteShowtime should call repo delete`() = runTest {
        coEvery { repo.deleteShowtime(any()) } returns DataResult.Success(1)

        service.deleteShowtime(1L)

        coVerify {
            repo.deleteShowtime(any())
        }
    }

    @Test
    fun `updateShowtimeStatus should return updated showtime`() = runTest {
        val status = ShowtimeStatus.COMPLETED
        coEvery {
            repo.updateShowtimeStatus(
                1L,
                status
            )
        } returns DataResult.Success(sampleShowtime.copy(status = status))

        val result = service.updateShowtimeStatus(1L, status)

        assertEquals(status, result.status)
    }

    @Test
    fun `service should throw InternalServerException on unknown error`() = runTest {
        coEvery { repo.getShowtimeById(1L) } returns DataResult.Failure.UnknownError("DB Error")

        assertThrows<InternalServerException> {
            service.getShowtimeById(1L)
        }
    }
}