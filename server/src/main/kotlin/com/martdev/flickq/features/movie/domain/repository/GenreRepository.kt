package com.martdev.flickq.features.movie.domain.repository

import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.shared.domain.model.DataResult

interface GenreRepository {
    suspend fun saveGenre(genre: Genre): DataResult<Genre>
    suspend fun getGenres(): DataResult<List<Genre>>
    suspend fun deleteGenre(id: Long): DataResult<Int>
}