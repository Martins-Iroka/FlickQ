package com.martdev.flickq.feature.movie.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.feature.movie.domain.MovieRepository
import com.martdev.flickq.movie.MovieDTO
import com.martdev.flickq.movie.MovieListItemDTO
import com.martdev.flickq.movie.model.Movie
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate

/**
 * Ktor-backed [MovieRepository]. `get-movies` returns lightweight list items (id/title/
 * poster); full details come from `get-movie-by-id`. Used when [com.martdev.flickq.core.data.AppConfig.USE_FAKES] is false.
 */
class RealMovieDataSource(
    private val client: HttpClient
) : MovieRepository {

    override suspend fun getMovies(
        limit: Int,
        offset: Int,
        date: LocalDate?
    ): Result<List<Movie>, DataError> =
        client.getData<List<MovieListItemDTO>>(
            "/movie/get-movies",
            queryParameters = mapOf("limit" to limit, "offset" to offset, "date" to date?.toString()),
        ).map { items -> items.map { it.toMovie() } }

    override suspend fun getMovieById(id: Long): Result<Movie, DataError> =
        client.getData<MovieDTO>("/movie/get-movie-by-id/$id")
            .map { it.toMovie() }
}
