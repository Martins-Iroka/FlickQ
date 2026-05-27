package com.martdev.flickq.features.room.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.room.domain.service.RoomService
import com.martdev.flickq.room.RoomDTO
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
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

const val roomPath = "/room"
const val adminRoomPath = "/admin/$roomPath"

fun Route.roomRoute() {
    val service by inject<RoomService>()
    adminRoomRoute(service)
    roomPublicRoute(service)
}

private fun Route.adminRoomRoute(service: RoomService) {
    authenticate(AUTH_JWT) {
        withRole(Role.ADMIN) {
            route(adminRoomPath) {
                post("/create-room") {
                    val room = call.receive<RoomDTO>().toRoom()
                    val createdRoom = service.createRoom(room).toRoomDTO()
                    val response = DataResponse(createdRoom)
                    call.respond(HttpStatusCode.Created, response)
                }
                put("/update-room/{room_id}") {
                    val roomId = getParameterFromPath("room_id")
                    val room = call.receive<RoomDTO>().toRoom().copy(id = roomId)
                    val updatedRoom = service.updateRoom(room).toRoomDTO()
                    val dataResponse = DataResponse(updatedRoom)
                    call.respond(HttpStatusCode.OK, dataResponse)
                }
                delete("/delete-room/{room_id}") {
                    val roomId = getParameterFromPath("room_id")
                    service.deleteRoom(roomId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun Route.roomPublicRoute(service: RoomService) {
    route(roomPath) {
        get("/get-rooms") {
            val rooms = service.getAllRooms().map {
                it.toRoomDTO()
            }
            val dataResponse = DataResponse(rooms)
            call.respond(HttpStatusCode.OK, dataResponse)
        }
        get("/get-room-by-id/{room_id}") {
            val roomId = getParameterFromPath("room_id")
            val room = service.getRoomById(roomId).toRoomDTO()
            val dataResponse = DataResponse(room)
            call.respond(HttpStatusCode.OK, dataResponse)
        }
    }
}