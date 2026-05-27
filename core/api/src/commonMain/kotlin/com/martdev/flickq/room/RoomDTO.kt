package com.martdev.flickq.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomDTO(
    val id: Long = 0,
    val name: String = "",
    val rows: Int = 0,
    val columns: Int = 0
)
