package com.martdev.flickq.features.movie.infrasturcture.repository

import com.martdev.flickq.features.movie.domain.repository.MovieRepository
import com.martdev.flickq.features.movie.infrasturcture.tables.GenreEntity
import com.martdev.flickq.features.movie.infrasturcture.tables.MovieGenreTable
import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesEntity
import com.martdev.flickq.features.movie.infrasturcture.tables.MoviesTable
import com.martdev.flickq.features.movie.infrasturcture.tables.toMovie
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.koin.core.annotation.Single

@Single
class MovieRepositoryImpl : MovieRepository {
    override suspend fun createMovie(movie: Movie): DataResult<Long> {
        return withSuspendTransaction {
            val movieEntity = MoviesEntity.new {
                title = movie.title
                description = movie.description
                posterUrl = movie.posterUrl
                duration = movie.duration
                releasedDate = movie.releasedDate
            }
            val result = linkGenres(movieEntity, movie.genres)
            if (result is DataResult.Failure.NotFound) {
                rollback()
                return@withSuspendTransaction result
            }
            DataResult.Success(movieEntity.id.value)
        }
    }

    override suspend fun getMovies(
        limit: Int,
        offset: Long
    ): DataResult<List<Movie>> {
        return withSuspendTransaction {
            val result = MoviesTable
                .select(
                    MoviesTable.id,
                    MoviesTable.title,
                    MoviesTable.posterUrl
                ).limit(limit).offset(offset)
                .orderBy(MoviesTable.createdAt, SortOrder.DESC)
                .map {
                    val id = it[MoviesTable.id].value
                    val title = it[MoviesTable.title]
                    val posterUrl = it[MoviesTable.posterUrl]
                    Movie(id = id, title = title, posterUrl = posterUrl)
                }

            DataResult.Success(result)
        }
    }

    override suspend fun getMovieById(movieId: Long): DataResult<Movie> {
        return withSuspendTransaction {
            val entity = MoviesEntity
                .findById(id = movieId)
                ?: return@withSuspendTransaction DataResult.Failure.NotFound("Movie with id $movieId not found")
            val movie = entity.toMovie()
            DataResult.Success(movie)
        }
    }

    override suspend fun updateMovie(movie: Movie): DataResult<Movie> {
        return withSuspendTransaction {
            val genres = movie.genres.map { g ->
                GenreEntity.findById(g.id) ?: run {
                    rollback()
                    return@withSuspendTransaction DataResult.Failure.NotFound("${g.name} genre doesn't exist")
                }
            }

            val movieEntity = MoviesEntity.findByIdAndUpdate(movie.id) {
                it.title = movie.title
                it.description = movie.description
                it.posterUrl = movie.posterUrl
                it.duration = movie.duration
                it.releasedDate = movie.releasedDate
                it.genres = SizedCollection(
                    genres
                )
            }
                ?: return@withSuspendTransaction DataResult.Failure.NotFound("Movie with id ${movie.id} doesn't exist")

            DataResult.Success(movieEntity.toMovie())
        }
    }

    override suspend fun deleteMovie(id: Long): DataResult<Int> {
        return withSuspendTransaction {
            val deletedRow = MoviesTable.deleteWhere {
                MoviesTable.id eq id
            }
            if (deletedRow == 0) {
                DataResult.Failure.UnknownError("Failed to delete movie")
            } else DataResult.Success(deletedRow)
        }
    }

    override suspend fun getMoviesByGenre(
        genreId: Long,
        limit: Int,
        offset: Long
    ): DataResult<List<Movie>> {
        return withSuspendTransaction {
            val genreEntity =
                GenreEntity.findById(genreId) ?: return@withSuspendTransaction DataResult.Failure.NotFound(
                    "Genre with id $genreId doesn't exist"
                )

            val movies = genreEntity.movies
                .limit(limit).offset(offset)
                .map {
                    Movie(it.id.value, it.title, posterUrl = it.posterUrl)
                }

            DataResult.Success(movies)
        }
    }

    private fun linkGenres(m: MoviesEntity, genres: List<Genre>): DataResult<Unit> {
        genres.forEach { g ->
            val genreEntity =
                GenreEntity.findById(g.id) ?: return DataResult.Failure.NotFound("${g.name} genre doesn't exist")
            MovieGenreTable.insert {
                it[movieId] = m.id
                it[genreId] = genreEntity.id
            }
        }
        return DataResult.Success(Unit)
    }
}