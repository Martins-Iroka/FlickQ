package com.martdev.flickq.features.movie.domain.service.genre

import com.martdev.flickq.movie.model.Genre

interface GenreService {
    suspend fun createGenre(genre: Genre)
    suspend fun getGenres(): List<Genre>
    suspend fun deleteGenre(id: Long)
}