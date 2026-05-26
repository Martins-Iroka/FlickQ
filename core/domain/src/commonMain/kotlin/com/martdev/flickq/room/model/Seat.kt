package com.martdev.flickq.room.model

data class Seat(
    val id: Long = 0,
    val roomId: Long = 0,
    val rowLabel: String = "",
    val seatNumber: Int = 0
)
