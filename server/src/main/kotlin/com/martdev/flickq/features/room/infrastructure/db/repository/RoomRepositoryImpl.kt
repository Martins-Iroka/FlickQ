package com.martdev.flickq.features.room.infrastructure.db.repository

import com.martdev.flickq.features.room.domain.repository.RoomRepository
import com.martdev.flickq.features.room.infrastructure.db.table.RoomEntity
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.room.infrastructure.db.table.toRoom
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.koin.core.annotation.Single

@Single
class RoomRepositoryImpl : RoomRepository {
    override suspend fun createRoom(room: Room): DataResult<Room> {
        return withSuspendTransaction {
            val savedRoom = RoomEntity.new {
                name = room.name
                rows = room.rows
                cols = room.columns
            }.toRoom()

            DataResult.Success(savedRoom)
        }
    }

    override suspend fun getAllRooms(): DataResult<List<Room>> {
        return withSuspendTransaction {
            val rooms = RoomEntity.all().map {
                it.toRoom()
            }
            DataResult.Success(rooms)
        }
    }

    override suspend fun getRoomById(roomId: Long): DataResult<Room> {
        return withSuspendTransaction {
            val room = RoomEntity.findById(roomId)?.toRoom()
                ?: return@withSuspendTransaction DataResult.Failure.NotFound("Room not found")

            DataResult.Success(room)
        }
    }

    override suspend fun updateRoom(room: Room): DataResult<Room> {
        return withSuspendTransaction {
            val updatedRoom = RoomEntity.findByIdAndUpdate(room.id) {
                it.name = room.name
                it.rows = room.rows
                it.cols = room.columns
            } ?: return@withSuspendTransaction DataResult.Failure.NotFound("Room not found for update")
            DataResult.Success(updatedRoom.toRoom())
        }
    }

    override suspend fun deleteRoom(roomId: Long): DataResult<Int> {
        return withSuspendTransaction {
            val deletedRow = RoomTable.deleteWhere {
                RoomTable.id eq roomId
            }
            if (deletedRow <= 0) {
                DataResult.Failure.NotFound("Room not found")
            } else {
                DataResult.Success(deletedRow)
            }
        }
    }
}