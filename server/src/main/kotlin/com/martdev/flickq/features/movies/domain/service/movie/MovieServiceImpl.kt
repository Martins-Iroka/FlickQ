package com.martdev.flickq.features.movies.domain.service.movie

import com.martdev.flickq.features.movies.domain.repository.MovieRepository
import com.martdev.flickq.movies.model.Movie
import com.martdev.flickq.shared.util.returnValue
import org.koin.core.annotation.Single

@Single
class MovieServiceImpl(
    private val movieRepository: MovieRepository
) : MovieService {
    override suspend fun createMovie(movie: Movie) {
        movieRepository.createMovie(movie).returnValue()
    }

    override suspend fun getMovies(
        limit: Int,
        offset: Long
    ): List<Movie> {
        return movieRepository.getMovies(limit, offset).returnValue()
    }

    override suspend fun getMovieById(movieId: Long): Movie {
        return movieRepository.getMovieById(movieId).returnValue()
    }

    override suspend fun updateMovie(movie: Movie): Movie {
        return movieRepository.updateMovie(movie).returnValue()
    }

    override suspend fun deleteMovie(id: Long) {
        movieRepository.deleteMovie(id).returnValue()
    }

    override suspend fun getMoviesByGenre(
        genreId: Long,
        limit: Int,
        offset: Long
    ): List<Movie> {
        return movieRepository.getMoviesByGenre(genreId, limit, offset).returnValue()
    }
}