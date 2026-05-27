package com.martdev.flickq.plugins

import com.martdev.flickq.features.auth.api.authRoutes
import com.martdev.flickq.features.movie.api.genre.genreRoute
import com.martdev.flickq.features.movie.api.movie.movieRoute
import com.martdev.flickq.features.room.api.roomRoute
import com.martdev.flickq.features.room.api.seatRoute
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

const val apiV1Path = "/api/v1"
fun Application.configureRouting() {
    routing {
        route(apiV1Path) {
            authRoutes()
            movieRoute()
            genreRoute()
            roomRoute()
            seatRoute()
            /*showtimeRoute()
            reservationRoute()
            paymentRoute()
            reportRoute()*/
        }
    }
}