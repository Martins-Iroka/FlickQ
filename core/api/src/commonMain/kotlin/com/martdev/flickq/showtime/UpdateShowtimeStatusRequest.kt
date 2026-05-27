package com.martdev.flickq.showtime

import kotlinx.serialization.Serializable

@Serializable
data class UpdateShowtimeStatusRequest(val status: String)