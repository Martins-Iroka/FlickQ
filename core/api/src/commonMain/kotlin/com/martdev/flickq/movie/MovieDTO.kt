package com.martdev.flickq.movie

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieData(
    val movies: List<MovieDTO>,
    @SerialName("next_offset")
    val nextOffset: Long
)

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
