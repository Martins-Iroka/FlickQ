package com.martdev.flickq.features.room.api

import com.martdev.flickq.room.RoomDTO
import com.martdev.flickq.room.SeatDTO
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat

fun RoomDTO.toRoom() = Room(
    name = name,
    rows = rows,
    columns = columns
)

fun Room.toRoomDTO() = RoomDTO(
    id, name, rows, columns
)

fun SeatDTO.toSeat() = Seat(
    roomId = roomId,
    rowLabel = rowLabel,
    seatNumber = seatNumber
)

fun Seat.toSeatDTO() = SeatDTO(
    id, roomId, rowLabel, seatNumber
)