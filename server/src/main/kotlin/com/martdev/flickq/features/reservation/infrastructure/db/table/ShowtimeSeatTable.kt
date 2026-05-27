package com.martdev.flickq.features.reservation.infrastructure.db.table

import com.martdev.flickq.features.room.infrastructure.db.table.SeatTable
import com.martdev.flickq.features.showtime.infrastructure.db.table.ShowtimeTable
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.SeatStatus
import com.martdev.flickq.reservation.model.ShowtimeSeat
import com.martdev.flickq.shared.infrastruce.db.setEnumeration
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

object ShowtimeSeatTable : LongIdTable("showtime_seats") {
    val showtimeId = reference("showtime_id", ShowtimeTable)
    val seatId = reference("seat_id", SeatTable)
    val reservationId = reference("reservation_id", ReservationTable).nullable()
    val status = setEnumeration<SeatStatus>("status", "seat_status")

    init {
        uniqueIndex(showtimeId, seatId)
    }
}

class ShowtimeSeatEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ShowtimeSeatEntity>(ShowtimeSeatTable)

    var showtimeId by ShowtimeSeatTable.showtimeId
    var seatId by ShowtimeSeatTable.seatId
    var reservationId by ShowtimeSeatTable.reservationId
    var status by ShowtimeSeatTable.status
}

fun ShowtimeSeatEntity.toShowtimeSeat() = ShowtimeSeat(
    id.value, showtimeId.value, seatId.value, reservationId?.value, status
)

fun ReservationEntity.toReservation(): Reservation {
    val seats = ShowtimeSeatEntity.find {
        ShowtimeSeatTable.reservationId eq id
    }.map { it.toShowtimeSeat() }

    return Reservation(
        id.value, userId.value, showtimeId.value, status, totalAmount, seats, createdAt, expiresAt
    )
}