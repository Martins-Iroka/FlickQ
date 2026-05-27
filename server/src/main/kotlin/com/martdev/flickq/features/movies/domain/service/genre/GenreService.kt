package com.martdev.flickq.features.movies.domain.service.genre

import com.martdev.flickq.movies.model.Genre

interface GenreService {
    suspend fun createGenre(genre: Genre)
    suspend fun getGenres(): List<Genre>
    suspend fun deleteGenre(id: Long)
}