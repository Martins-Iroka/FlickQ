package com.martdev.flickq.plugins

import com.martdev.flickq.features.auth.api.authRoutes
import com.martdev.flickq.features.movie.api.genre.genreRoute
import com.martdev.flickq.features.movie.api.movie.movieRoute
import com.martdev.flickq.features.payment.domain.api.paymentRoute
import com.martdev.flickq.features.report.api.reportRoute
import com.martdev.flickq.features.reservation.api.reservationRoute
import com.martdev.flickq.features.room.api.roomRoute
import com.martdev.flickq.features.room.api.seatRoute
import com.martdev.flickq.features.showtime.api.showtimeRoute
import com.martdev.flickq.shared.DataResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

const val apiV1Path = "/api/v1"
fun Application.configureRouting() {
    routing {
        route("/") {
            get("health") {
                call.respond(HttpStatusCode.OK, DataResponse("OK"))
            }
        }
        route(apiV1Path) {
            authRoutes()
            movieRoute()
            genreRoute()
            roomRoute()
            seatRoute()
            showtimeRoute()
            reservationRoute()
            paymentRoute()
            reportRoute()
        }
    }
}