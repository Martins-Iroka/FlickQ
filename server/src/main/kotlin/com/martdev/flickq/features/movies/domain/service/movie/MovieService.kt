package com.martdev.flickq.features.movies.domain.service.movie

import com.martdev.flickq.movies.model.Movie

interface MovieService {
    suspend fun createMovie(movie: Movie)
    suspend fun getMovies(limit: Int, offset: Long): List<Movie>
    suspend fun getMovieById(movieId: Long): Movie
    suspend fun updateMovie(movie: Movie): Movie
    suspend fun deleteMovie(id: Long)
    suspend fun getMoviesByGenre(genreId: Long, limit: Int, offset: Long): List<Movie>
}