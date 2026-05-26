package com.martdev.flickq.showtime.model

import kotlin.time.Clock
import kotlin.time.Instant

private val defaultLocalDateTime = Clock.System.now()

data class Showtime(
    val id: Long = 0,
    val movieId: Long = 0,
    val roomId: Long = 0,
    val startsAt: Instant = defaultLocalDateTime,
    val endsAt: Instant = defaultLocalDateTime,
    val price: Int = 0,
    val status: ShowtimeStatus = ShowtimeStatus.SCHEDULED
)
