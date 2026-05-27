package com.martdev.flickq.movies

import kotlinx.serialization.Serializable

@Serializable
data class MovieListItemDTO(
    val id: Long,
    val title: String,
    val posterUrl: String
)
