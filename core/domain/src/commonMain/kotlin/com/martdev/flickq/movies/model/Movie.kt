package com.martdev.flickq.movies.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class Movie(
    val id: Long = 0L,
    val title: String = "",
    val description: String = "",
    val posterUrl: String = "",
    val duration: Int = 0,
    val releasedDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
    val genres: List<Genre> = emptyList()
)
