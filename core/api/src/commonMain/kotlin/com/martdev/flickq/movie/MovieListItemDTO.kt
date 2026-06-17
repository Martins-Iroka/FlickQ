package com.martdev.flickq.movie

import kotlinx.serialization.Serializable

@Serializable
data class MovieListItemDTO(
    val id: Long,
    val title: String,
    val posterUrl: String,
    val duration: Int,
    val releasedDate: String,
    val genres: List<GenreDTO> = emptyList()
)
