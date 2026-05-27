package com.martdev.flickq.features.room.infrastructure.db.table

import com.martdev.flickq.room.model.Seat
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

object SeatTable : LongIdTable("seats") {
    val roomId = reference("room_id", RoomTable, onDelete = ReferenceOption.CASCADE)
    val rowLabel = text("row_label")
    val seatNumber = integer("seat_number")

    init {
        uniqueIndex(roomId, rowLabel, seatNumber)
    }
}

class SeatEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SeatEntity>(SeatTable)

    var roomId by SeatTable.roomId
    var rowLabel by SeatTable.rowLabel
    var seatNumber by SeatTable.seatNumber
}

fun SeatEntity.toSeat() = Seat(
    id.value, roomId.value, rowLabel, seatNumber
)