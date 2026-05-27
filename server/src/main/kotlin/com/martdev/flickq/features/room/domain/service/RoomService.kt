package com.martdev.flickq.features.room.domain.service

import com.martdev.flickq.room.model.Room

interface RoomService {
    suspend fun createRoom(room: Room): Room
    suspend fun getAllRooms(): List<Room>
    suspend fun getRoomById(roomId: Long): Room
    suspend fun updateRoom(room: Room): Room
    suspend fun deleteRoom(roomId: Long)
}