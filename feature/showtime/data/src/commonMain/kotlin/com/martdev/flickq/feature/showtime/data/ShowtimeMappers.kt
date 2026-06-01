package com.martdev.flickq.feature.showtime.data

import com.martdev.flickq.showtime.ShowtimeDTO
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlin.time.Clock

internal fun ShowtimeDTO.toShowtime(): Showtime = Showtime(
    id = id,
    movieId = movieId,
    roomId = roomId,
    startsAt = startsAt ?: Clock.System.now(),
    endsAt = endsAt ?: Clock.System.now(),
    price = price,
    status = status.toShowtimeStatus()
)

/**
 * The server `status` is a free string. Unknown values map to [ShowtimeStatus.CANCELLED] so
 * an unrecognised showtime is treated as not bookable rather than silently shown as scheduled.
 */
internal fun String.toShowtimeStatus(): ShowtimeStatus =
    runCatching { ShowtimeStatus.valueOf(uppercase()) }.getOrDefault(ShowtimeStatus.CANCELLED)
