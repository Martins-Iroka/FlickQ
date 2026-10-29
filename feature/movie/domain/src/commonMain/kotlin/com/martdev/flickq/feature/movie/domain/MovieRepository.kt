package com.martdev.flickq.feature.movie.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.movie.model.MovieDataModel
import kotlinx.datetime.LocalDate

interface MovieRepository {
    suspend fun getMovieById(id: Long): Result<Movie, DataError>

    suspend fun getScheduledMovies(date: LocalDate, limit: Int, offset: Long): Result<MovieDataModel, DataError>
}
