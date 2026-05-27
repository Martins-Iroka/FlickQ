package com.martdev.flickq.movies

import kotlinx.serialization.Serializable

@Serializable
data class MovieDTO(
    val id: Long = 0L,
    val title: String = "",
    val description: String = "",
    val posterUrl: String = "",
    val duration: Int = 0,
    val releasedDate: String = "",
    val genres: List<GenreDTO> = emptyList()
)
