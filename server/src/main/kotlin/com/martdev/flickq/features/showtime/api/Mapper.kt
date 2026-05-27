package com.martdev.flickq.features.showtime.api

import com.martdev.flickq.showtime.ShowtimeDTO
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus

fun ShowtimeDTO.toShowtime() = Showtime(
    movieId = movieId,
    roomId = roomId,
    startsAt = startsAt!!,
    endsAt = endsAt!!,
    price = price,
    status = ShowtimeStatus.valueOf(status)
)

fun ShowtimeDTO.toShowtime(id: Long) = Showtime(
    id = id,
    movieId = movieId,
    roomId = roomId,
    startsAt = startsAt!!,
    endsAt = endsAt!!,
    price = price,
    status = ShowtimeStatus.valueOf(status)
)

fun Showtime.toShowtimeDTO() = ShowtimeDTO(
    id, movieId, roomId, startsAt, endsAt, price, status.name
)