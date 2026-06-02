package com.martdev.flickq.feature.movie.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.movie.model.Movie

interface MovieRepository {
    /** A page of the catalog. The server applies [limit]/[offset]; callers paginate via offset. */
    suspend fun getMovies(limit: Int = 20, offset: Int = 0): Result<List<Movie>, DataError>

    suspend fun getMovieById(id: Long): Result<Movie, DataError>
}
