package com.martdev.flickq.showtime

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class ShowtimeDTO(
    val id: Long = 0L,
    val movieId: Long = 0L,
    val roomId: Long = 0L,
    val startsAt: Instant? = Clock.System.now(),
    val endsAt: Instant? = Clock.System.now(),
    val price: Int = 0,
    val status: String = ""
)
