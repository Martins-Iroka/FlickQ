package com.martdev.flickq.features.showtime.infrastructure.db.table

import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.shared.infrastruce.db.setEnumeration
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object ShowtimeTable : LongIdTable("show_times") {
    val movieId = reference("movie_id", MoviesTable.id)
    val roomId = reference("room_id", RoomTable.id)
    val startsAt = timestamp("starts_at")
    val endsAt = timestamp("ends_at")
    val price = integer("price")
    val status = setEnumeration<ShowtimeStatus>("status", "showtime_status")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

class ShowtimeEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ShowtimeEntity>(ShowtimeTable)

    var movieId by ShowtimeTable.movieId
    var roomId by ShowtimeTable.roomId
    var startsAt by ShowtimeTable.startsAt
    var endsAt by ShowtimeTable.endsAt
    var price by ShowtimeTable.price
    var status by ShowtimeTable.status
}

fun ShowtimeEntity.toShowtime() = Showtime(
    id.value, movieId.value, roomId.value, startsAt, endsAt, price, status
)