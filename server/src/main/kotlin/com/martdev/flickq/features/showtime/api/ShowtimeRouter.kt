package com.martdev.flickq.features.showtime.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.showtime.domain.service.ShowtimeService
import com.martdev.flickq.shared.api.AUTH_JWT
import com.martdev.flickq.shared.api.DataResponse
import com.martdev.flickq.shared.api.withRole
import com.martdev.flickq.showtime.ShowtimeDTO
import com.martdev.flickq.showtime.UpdateShowtimeStatusRequest
import com.martdev.flickq.showtime.model.ShowtimeStatus
import com.martdev.shared.api.getLimitAndOffset
import com.martdev.shared.api.getParameterFromPath
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

const val showtimePath = "/showtime"
const val adminShowtimePath = "/admin$showtimePath"

fun Route.showtimeRoute() {
    val service by inject<ShowtimeService>()
    adminShowtime(service)
    showtimePublicRoute(service)
}

private fun Route.adminShowtime(service: ShowtimeService) {
    authenticate(AUTH_JWT) {
        withRole(Role.ADMIN) {
            route(adminShowtimePath) {
                post("/create-showtime") {
                    val showtime = call.receive<ShowtimeDTO>().toShowtime()
                    val result = service.createShowtime(showtime).toShowtimeDTO()
                    val response = DataResponse(result)
                    call.respond(status = HttpStatusCode.Created, response)
                }

                put("/update-showtime/{showtime_id}") {
                    val showtimeId = getParameterFromPath("showtime_id")
                    val showtime = call.receive<ShowtimeDTO>().toShowtime(showtimeId)
                    val result = service.updateShowtime(showtime).toShowtimeDTO()
                    val response = DataResponse(result)
                    call.respond(status = HttpStatusCode.OK, response)
                }

                delete("/delete-showtime/{showtime_id}") {
                    val showtimeId = getParameterFromPath("showtime_id")
                    service.deleteShowtime(showtimeId)
                    call.respond(HttpStatusCode.NoContent)
                }

                patch("/update-showtime-status/{showtime_id}") {
                    val showtimeId = getParameterFromPath("showtime_id")
                    val showtimeStatus = call.receive<UpdateShowtimeStatusRequest>().status
                    val result =
                        service.updateShowtimeStatus(showtimeId, ShowtimeStatus.valueOf(showtimeStatus.uppercase()))
                            .toShowtimeDTO()
                    val response = DataResponse(result)
                    call.respond(status = HttpStatusCode.OK, response)
                }

                get("/get-showtimes") {
                    val (limit, offset) = getLimitAndOffset()
                    val result = service.getShowtimes(limit, offset).map {
                        it.toShowtimeDTO()
                    }
                    val response = DataResponse(result)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }
    }
}

private fun Route.showtimePublicRoute(service: ShowtimeService) {
    route(showtimePath) {
        get("/get-showtimes-by-movie-id/{movie_id}") {
            val movieId = getParameterFromPath("movie_id")
            val result = service.getShowtimesByMovieId(movieId).map {
                it.toShowtimeDTO()
            }
            val response = DataResponse(result)
            call.respond(HttpStatusCode.OK, response)
        }

        get("/get-showtime-by-id/{showtime_id}") {
            val showtimeId = getParameterFromPath("showtime_id")
            val result = service.getShowtimeById(showtimeId).toShowtimeDTO()
            val response = DataResponse(result)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}