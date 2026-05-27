package com.martdev.flickq.features.movies.api.genre

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.movies.api.toGenre
import com.martdev.flickq.features.movies.api.toGenreDto
import com.martdev.flickq.features.movies.domain.service.genre.GenreService
import com.martdev.flickq.movies.GenreDTO
import com.martdev.flickq.shared.api.AUTH_JWT
import com.martdev.flickq.shared.api.DataResponse
import com.martdev.flickq.shared.api.withRole
import com.martdev.shared.api.getParameterFromPath
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

const val genrePath = "/genre"
const val adminGenrePath = "/admin/$genrePath"

fun Route.genreRoute() {
    val service by inject<GenreService>()
    adminGenreRoute(service)
    genrePublicRoute(service)
}

private fun Route.adminGenreRoute(service: GenreService) {
    authenticate(AUTH_JWT) {
        withRole(Role.ADMIN) {
            route(adminGenrePath) {

                post("/create-genre") {
                    val genre = call.receive<GenreDTO>().toGenre()
                    service.createGenre(genre)
                    call.respond(HttpStatusCode.Created)
                }

                delete("/delete-genre/{genre_id}") {
                    val genreId = getParameterFromPath("genre_id")
                    service.deleteGenre(genreId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun Route.genrePublicRoute(service: GenreService) {
    route(genrePath) {
        get("/genres") {
            val genres = service.getGenres().map {
                it.toGenreDto()
            }
            val dataResponse = DataResponse(genres)
            call.respond(HttpStatusCode.OK, dataResponse)
        }
    }
}