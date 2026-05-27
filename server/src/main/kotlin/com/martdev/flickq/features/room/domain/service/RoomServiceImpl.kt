package com.martdev.flickq.features.room.domain.service

import com.martdev.flickq.features.room.domain.repository.RoomRepository
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.shared.util.returnValue
import org.koin.core.annotation.Single

@Single
class RoomServiceImpl(
    private val repository: RoomRepository
) : RoomService {
    override suspend fun createRoom(room: Room): Room {
        return repository.createRoom(room).returnValue()
    }

    override suspend fun getAllRooms(): List<Room> {
        return repository.getAllRooms().returnValue()
    }

    override suspend fun getRoomById(roomId: Long): Room {
        return repository.getRoomById(roomId).returnValue()
    }

    override suspend fun updateRoom(room: Room): Room {
        return repository.updateRoom(room).returnValue()
    }

    override suspend fun deleteRoom(roomId: Long) {
        repository.deleteRoom(roomId).returnValue()
    }
}