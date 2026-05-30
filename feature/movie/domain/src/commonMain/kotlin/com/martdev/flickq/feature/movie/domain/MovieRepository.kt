package com.martdev.flickq.feature.movie.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.movie.model.Movie

interface MovieRepository {
    suspend fun getMovies(): Result<List<Movie>, DataError>

    suspend fun getMovieById(id: Long): Result<Movie, DataError>
}
