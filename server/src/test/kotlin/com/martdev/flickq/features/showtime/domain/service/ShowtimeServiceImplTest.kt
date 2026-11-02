package com.martdev.flickq.features.showtime.domain.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.martdev.flickq.features.movie.domain.service.movie.MovieService
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.repository.NotificationEnqueuer
import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

@ExtendWith(MockKExtension::class)
class ShowtimeServiceImplTest {
    @MockK
    private lateinit var repo: ShowtimeRepository
    @MockK
    private lateinit var movieService: MovieService
    @MockK
    private lateinit var notificationEnqueuer: NotificationEnqueuer
    private lateinit var service: ShowtimeService

    private val sampleShowtime = Showtime(id = 1, movieId = 1, roomId = 1)

    @BeforeEach
    fun setUp() {
        service = ShowtimeServiceImpl(repo, movieService, notificationEnqueuer)
    }

    @Test
    fun `createShowtime should return showtime on success then get movies and push notification`() = runTest {
        val movieIdSlot = slot<Long>()
        val notificationEventSlot = slot<NotificationEvent>()
        coEvery { repo.createShowtime(any()) } returns DataResult.Success(sampleShowtime)

        coEvery { movieService.getMovieById(capture(movieIdSlot)) } returns Movie(
            title = "Apex", posterUrl = "image_url"
        )

        coEvery {
            notificationEnqueuer.suspendedEnqueueInCurrentTx(capture(notificationEventSlot))
        } returns DataResult.Success(true)

        val result = service.createShowtime(sampleShowtime)

        assertEquals(sampleShowtime, result)
        assertThat(movieIdSlot.captured).isEqualTo(sampleShowtime.movieId)
        assertThat(notificationEventSlot.captured).isEqualTo(
            NotificationEvent.MovieAdded(
                sampleShowtime.movieId, "Apex", "image_url"
            )
        )
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

    @Test
    fun `get showtime movies ids`() = runTest {
        coEvery { repo.getShowtimeMovieIds(any()) } returns DataResult.Success(
            listOf(1, 2, 3)
        )
        val today = Clock.System.todayIn(TimeZone.UTC)
        val result = service.getShowtimeMovieIds(today)

        assertTrue(result.isNotEmpty())
        assertEquals(3, result.size)
    }

    @Test
    fun `get showtime movies returns success`() = runTest {
        coEvery {
            repo.getShowtimeMovieIds(any())
        } returns DataResult.Success(listOf(1))

        coEvery {
            movieService.getMovies(any(), any(), any())
        } returns listOf(Movie())

        val result = service.getShowtimeMovies(date = Clock.System.todayIn(TimeZone.UTC), 1, 2)

        assertThat(result).isNotEmpty()
        assertThat(result).hasSize(1)
    }
}