package com.martdev.flickq.feature.admin.presentation.logic.rooms

import kotlinx.serialization.Serializable

@Serializable
data class RoomData(
    val id: Long = 0,
    val name: String = "",
    val rows: Int = 0,
    val columns: Int = 0
)
