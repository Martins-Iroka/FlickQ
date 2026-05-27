package com.martdev.flickq.features.room.domain.service

import com.martdev.flickq.room.model.Seat

interface SeatService {
    suspend fun createSeats(seats: List<Seat>): List<Seat>
    suspend fun getSeatsByRoomId(roomId: Long): List<Seat>
    suspend fun getSeatById(seatId: Long): Seat
}