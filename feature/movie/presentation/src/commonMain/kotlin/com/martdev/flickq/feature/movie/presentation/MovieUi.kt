package com.martdev.flickq.feature.movie.presentation

import com.martdev.flickq.movie.model.Movie

data class MovieUi(
    val id: Long,
    val title: String,
    val description: String,
    val posterUrl: String,
    val year: String,
    val duration: String,
    val genres: String
)

fun Movie.toMovieUi(): MovieUi = MovieUi(
    id = id,
    title = title,
    description = description,
    posterUrl = posterUrl,
    year = releasedDate.year.toString(),
    duration = formatDuration(duration),
    genres = genres.joinToString(" • ") { it.name }
)

private fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return ""
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}
