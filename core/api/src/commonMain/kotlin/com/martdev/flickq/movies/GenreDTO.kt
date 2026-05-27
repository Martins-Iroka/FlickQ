package com.martdev.flickq.movies

import kotlinx.serialization.Serializable

@Serializable
data class GenreDTO(
    val id: Long = 0L,
    val name: String
)
