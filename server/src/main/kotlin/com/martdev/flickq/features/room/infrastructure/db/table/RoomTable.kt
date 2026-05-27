package com.martdev.flickq.features.room.infrastructure.db.table

import com.martdev.flickq.room.model.Room
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

object RoomTable : LongIdTable("rooms") {
    val name = varchar("name", 255)
    val rows = integer("rows")
    val cols = integer("cols")
}

class RoomEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<RoomEntity>(RoomTable)

    var name by RoomTable.name
    var rows by RoomTable.rows
    var cols by RoomTable.cols
}

fun RoomEntity.toRoom() = Room(
    id = id.value,
    name = name,
    rows = rows,
    cols
)