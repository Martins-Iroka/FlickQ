package com.martdev.flickq.features.showtime.domain.service

import com.martdev.flickq.features.showtime.domain.repository.ShowtimeRepository
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.util.returnValue
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import org.koin.core.annotation.Single

@Single
class ShowtimeServiceImpl(
    private val repo: ShowtimeRepository
) : ShowtimeService {
    override suspend fun createShowtime(showtime: Showtime): Showtime {
        return when (val result = repo.createShowtime(showtime)) {
            is DataResult.Failure.Conflict -> throw ConflictException("Room is already booked for this time slot")
            is DataResult.Failure.ForeignKeyViolation -> throw NotFoundException("Movie or room not found")
            is DataResult.Success -> result.value
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
}