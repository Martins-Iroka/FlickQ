package com.martdev.flickq.features.showtime.domain.service

import com.martdev.flickq.features.movie.domain.service.movie.MovieService
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.repository.NotificationEnqueuer
import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.util.getLoggerFactory
import com.martdev.flickq.shared.util.returnValue
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Singleton

@Singleton
class ShowtimeServiceImpl(
    private val repo: ShowtimeRepository,
    private val movieService: MovieService,
    private val enqueuer: NotificationEnqueuer

) : ShowtimeService {
    private val log = getLoggerFactory(this::class.java.simpleName)

    override suspend fun createShowtime(showtime: Showtime): Showtime {
        return when (val result = repo.createShowtime(showtime)) {
            is DataResult.Failure.Conflict -> throw ConflictException("Room is already booked for this time slot")
            is DataResult.Failure.ForeignKeyViolation -> throw NotFoundException("Movie or room not found")
            is DataResult.Success -> {
                val showtime = result.value
                try {
                    val movie = movieService.getMovieById(showtime.movieId)
                    enqueuer.suspendedEnqueueInCurrentTx(
                        NotificationEvent.MovieAdded(
                            showtime.movieId, movie.title, movie.posterUrl
                        )
                    )
                } catch (e: Exception) {
                    log.warn("Failed to send notification due to {}", e.message)
                }
                showtime
            }
            else -> throw InternalServerException()
        }
    }

    override suspend fun getShowtimes(
        limit: Int,
        offset: Long
    ): List<Showtime> {
        return repo.getShowtimes(limit, offset).returnValue()
    }

    override suspend fun getShowtimesByMovieId(movieId: Long): List<Showtime> {
        return repo.getShowtimesByMovieId(movieId).returnValue()
    }

    override suspend fun getShowtimeById(id: Long): Showtime {
        return repo.getShowtimeById(id).returnValue()
    }

    override suspend fun updateShowtime(showtime: Showtime): Showtime {
        return when (val result = repo.updateShowtime(showtime)) {
            is DataResult.Success -> result.value
            is DataResult.Failure.Conflict -> throw ConflictException("Room is already booked for this time slot")
            is DataResult.Failure.NotFound -> throw NotFoundException("Showtime not found")
            else -> throw InternalServerException()
        }
    }

    override suspend fun deleteShowtime(id: Long) {
        repo.deleteShowtime(id).returnValue()
    }

    override suspend fun updateShowtimeStatus(
        id: Long,
        status: ShowtimeStatus
    ): Showtime {
        return repo.updateShowtimeStatus(id, status).returnValue()
    }

    override suspend fun getShowtimeMovieIds(date: LocalDate): List<Long> {
        return repo.getShowtimeMovieIds(date).returnValue()
    }

    override suspend fun getShowtimeMovies(date: LocalDate, limit: Int, offset: Long): List<Movie> {
        val movieIds = repo.getShowtimeMovieIds(date).returnValue()
        return movieService.getMovies(movieIds, limit, offset)
    }
}