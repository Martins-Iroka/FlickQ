package com.martdev.flickq.feature.admin.presentation.logic.showtimes

import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.serialization.Serializable

@Serializable
data class ShowtimeData(
    val editingId: Long = 0,
    val movieId: String = "",
    val roomId: String = "",
    val startsAt: String = "",
    val endsAt: String = "",
    val price: String = "",
    val status: ShowtimeStatus = ShowtimeStatus.SCHEDULED,
)