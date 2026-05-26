package com.martdev.flickq.plugins

import com.martdev.flickq.features.auth.api.authRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

const val apiV1Path = "/v1/api"
fun Application.configureRouting() {
    routing {
        route(apiV1Path) {
            authRoutes()
            /*movieRoute()
            genreRoute()
            roomRoute()
            seatRoute()
            showtimeRoute()
            reservationRoute()
            paymentRoute()
            reportRoute()*/
        }
    }
}