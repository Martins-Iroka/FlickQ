package com.martdev.flickq.features.room.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.room.domain.service.SeatService
import com.martdev.flickq.room.SeatDTO
import com.martdev.flickq.shared.api.AUTH_JWT
import com.martdev.flickq.shared.api.DataResponse
import com.martdev.flickq.shared.api.withRole
import com.martdev.shared.api.getParameterFromPath
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

const val seatPath = "/seat"
const val adminSeatPath = "/admin/$seatPath"

fun Route.seatRoute() {
    val service by inject<SeatService>()
    adminSeatRoute(service)
    seatPublicRoute(service)
}

private fun Route.adminSeatRoute(service: SeatService) {
    authenticate(AUTH_JWT) {
        withRole(Role.ADMIN) {
            route(adminSeatPath) {
                post("/create-seats") {
                    val seats = call.receive<List<SeatDTO>>().map { it.toSeat() }
                    val createdSeats = service.createSeats(seats).map { it.toSeatDTO() }
                    val response = DataResponse(createdSeats)
                    call.respond(HttpStatusCode.Created, response)
                }
            }
        }
    }
}

private fun Route.seatPublicRoute(service: SeatService) {
    route(seatPath) {
        get("/get-seat-by-id/{seat_id}") {
            val seatId = getParameterFromPath("seat_id")
            val seat = service.getSeatById(seatId).toSeatDTO()
            val dataResponse = DataResponse(seat)
            call.respond(HttpStatusCode.OK, dataResponse)
        }

        get("/get-seats-by-room-id/{room_id}") {
            val roomId = getParameterFromPath("room_id")
            val seats = service.getSeatsByRoomId(roomId).map { it.toSeatDTO() }
            val response = DataResponse(seats)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}