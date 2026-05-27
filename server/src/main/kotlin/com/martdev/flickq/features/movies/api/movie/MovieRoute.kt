package com.martdev.flickq.features.movies.api.movie

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.movies.api.toMovie
import com.martdev.flickq.features.movies.api.toMovieDto
import com.martdev.flickq.features.movies.api.toMovieItemDto
import com.martdev.flickq.features.movies.domain.service.movie.MovieService
import com.martdev.flickq.movies.MovieDTO
import com.martdev.flickq.shared.api.AUTH_JWT
import com.martdev.flickq.shared.api.DataResponse
import com.martdev.flickq.shared.api.withRole
import com.martdev.shared.api.getLimitAndOffset
import com.martdev.shared.api.getParameterFromPath
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

const val moviePath = "/movie"
const val adminMoviePath = "/admin/$moviePath"

fun Route.movieRoute() {
    val service by inject<MovieService>()
    adminMovieRoute(service)
    moviePublicRoute(service)
}

private fun Route.adminMovieRoute(service: MovieService) {
    authenticate(AUTH_JWT) {
        withRole(Role.ADMIN) {
            route(adminMoviePath) {
                post("/create-movie") {
                    val movie = call.receive<MovieDTO>().toMovie()
                    service.createMovie(movie)
                    call.respond(HttpStatusCode.Created)
                }

                put("/update-movie/{movie_id}") {
                    val movieId = getParameterFromPath("movie_id")
                    val movie = call.receive<MovieDTO>().toMovie().copy(id = movieId)
                    val updatedMovie = service.updateMovie(movie).toMovieDto()
                    val dataResponse = DataResponse(updatedMovie)
                    call.respond(HttpStatusCode.OK, dataResponse)
                }

                delete("/delete-movie/{movie_id}") {
                    val movieId = getParameterFromPath("movie_id")
                    service.deleteMovie(movieId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun Route.moviePublicRoute(service: MovieService) {
    route(moviePath) {
        get("/get-movies") {
            val (limit, offset) = getLimitAndOffset()
            val response = service.getMovies(limit, offset).map {
                it.toMovieItemDto()
            }
            val dataResponse = DataResponse(response)
            call.respond(HttpStatusCode.OK, dataResponse)
        }

        get("/get-movie-by-id/{movie_id}") {
            val movieId = getParameterFromPath("movie_id")
            val movie = service.getMovieById(movieId)
            val response = movie.toMovieDto()
            val dataResponse = DataResponse(response)
            call.respond(HttpStatusCode.OK, dataResponse)
        }

        get("/get-movies-by-genre/{genre-id}") {
            val genreId = getParameterFromPath("genre-id")
            val (limit, offset) = getLimitAndOffset()
            val response = service.getMoviesByGenre(genreId, limit, offset).map {
                it.toMovieItemDto()
            }
            val dataResponse = DataResponse(response)
            call.respond(HttpStatusCode.OK, dataResponse)
        }
    }
}