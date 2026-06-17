package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.movie.GenreDTO
import com.martdev.flickq.movie.MovieDTO
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.room.RoomDTO
import com.martdev.flickq.room.SeatDTO
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.showtime.ShowtimeDTO
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

// --- Movies & genres ------------------------------------------------------------------

internal fun GenreDTO.toGenre(): Genre = Genre(id = id, name = name)
internal fun Genre.toDto(): GenreDTO = GenreDTO(id = id, name = name)

internal fun MovieDTO.toMovie(): Movie = Movie(
    id = id,
    title = title,
    description = description,
    posterUrl = posterUrl,
    duration = duration,
    releasedDate = releasedDate.toLocalDateOrToday(),
    genres = genres.map { it.toGenre() },
)

internal fun Movie.toDto(): MovieDTO = MovieDTO(
    id = id,
    title = title,
    description = description,
    posterUrl = posterUrl,
    duration = duration,
    releasedDate = releasedDate.toString(),
    genres = genres.map { it.toDto() },
)

private fun String.toLocalDateOrToday(): LocalDate =
    takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).date

// --- Rooms & seats --------------------------------------------------------------------

internal fun RoomDTO.toRoom(): Room = Room(id = id, name = name, rows = rows, columns = columns)
internal fun Room.toDto(): RoomDTO = RoomDTO(id = id, name = name, rows = rows, columns = columns)

internal fun SeatDTO.toSeat(): Seat =
    Seat(id = id, roomId = roomId, rowLabel = rowLabel, seatNumber = seatNumber)

internal fun Seat.toDto(): SeatDTO =
    SeatDTO(id = id, roomId = roomId, rowLabel = rowLabel, seatNumber = seatNumber)

// --- Showtimes ------------------------------------------------------------------------

internal fun ShowtimeDTO.toShowtime(): Showtime = Showtime(
    id = id,
    movieId = movieId,
    roomId = roomId,
    startsAt = startsAt ?: Clock.System.now(),
    endsAt = endsAt ?: Clock.System.now(),
    price = price,
    status = status.toShowtimeStatus(),
)

internal fun Showtime.toDto(): ShowtimeDTO = ShowtimeDTO(
    id = id,
    movieId = movieId,
    roomId = roomId,
    startsAt = startsAt,
    endsAt = endsAt,
    price = price,
    status = status.name,
)

/** The server `status` is a free string; unknown values default to SCHEDULED for admin display. */
internal fun String.toShowtimeStatus(): ShowtimeStatus =
    runCatching { ShowtimeStatus.valueOf(uppercase()) }.getOrDefault(ShowtimeStatus.SCHEDULED)
