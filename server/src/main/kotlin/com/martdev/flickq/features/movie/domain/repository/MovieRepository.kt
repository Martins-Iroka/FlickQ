package com.martdev.flickq.features.movie.domain.repository

import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.shared.domain.model.DataResult

interface MovieRepository {
    suspend fun createMovie(movie: Movie): DataResult<Long>
    suspend fun getMovies(limit: Int, offset: Long): DataResult<List<Movie>>
    suspend fun getMovieById(movieId: Long): DataResult<Movie>
    suspend fun updateMovie(movie: Movie): DataResult<Movie>
    suspend fun deleteMovie(id: Long): DataResult<Int>
    suspend fun getMoviesByGenre(genreId: Long, limit: Int, offset: Long): DataResult<List<Movie>>
}