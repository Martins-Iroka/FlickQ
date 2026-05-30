package com.martdev.flickq.feature.showtime.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.showtime.domain.ShowtimeRepository
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Generates an in-memory showtime schedule (next two days, four screenings/day across
 * three screens) for every movie in the catalog. Swapped for a Ktor-backed
 * implementation (mapping ShowtimeDTO -> Showtime) when wiring the real API.
 */
class FakeShowtimeDataSource : ShowtimeRepository {

    private val schedule: List<Showtime> = buildSchedule()

    override suspend fun getShowtimesByMovieId(movieId: Long): Result<List<Showtime>, DataError> =
        Result.Success(schedule.filter { it.movieId == movieId })

    override suspend fun getShowtimeById(id: Long): Result<Showtime, DataError> =
        schedule.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)

    private fun buildSchedule(): List<Showtime> {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val startHours = listOf(13, 16, 19, 22)
        val prices = mapOf(1L to 3500, 2L to 3000, 3L to 2500, 4L to 4000, 5L to 3500, 6L to 3000)
        val runtime = 120.minutes

        var idSeq = 1L
        return buildList {
            for (movieId in 1L..6L) {
                for (dayOffset in 0..1) {
                    val date = today.plus(dayOffset, DateTimeUnit.DAY)
                    startHours.forEachIndexed { index, hour ->
                        val startsAt = LocalDateTime(date, LocalTime(hour, 0)).toInstant(tz)
                        add(
                            Showtime(
                                id = idSeq++,
                                movieId = movieId,
                                roomId = (index % 3) + 1L,
                                startsAt = startsAt,
                                endsAt = startsAt + runtime,
                                price = prices[movieId] ?: 3000,
                                status = ShowtimeStatus.SCHEDULED
                            )
                        )
                    }
                }
            }
        }
    }
}
