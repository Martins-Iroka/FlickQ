package com.martdev.flickq.feature.movie.data

import com.martdev.flickq.movie.GenreDTO
import com.martdev.flickq.movie.MovieDTO
import com.martdev.flickq.movie.MovieListItemDTO
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

internal fun MovieDTO.toMovie(): Movie = Movie(
    id = id,
    title = title,
    description = description,
    posterUrl = posterUrl,
    duration = duration,
    releasedDate = releasedDate.toLocalDateOrToday(),
    genres = genres.map { it.toGenre() }
)

/**
 * The list endpoint (`/movie/get-movies`) returns only id/title/poster; the remaining
 * fields are populated by the detail call (`/movie/get-movie-by-id`).
 */
internal fun MovieListItemDTO.toMovie(): Movie = Movie(
    id = id,
    title = title,
    posterUrl = posterUrl
)

internal fun GenreDTO.toGenre(): Genre = Genre(id = id, name = name)

private fun String.toLocalDateOrToday(): LocalDate =
    takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).date
