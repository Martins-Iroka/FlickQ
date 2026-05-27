package com.martdev.flickq.features.reservation.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.reservation.domain.service.ReservationCancellationService
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import com.martdev.flickq.features.reservation.domain.service.ShowtimeSeatService
import com.martdev.flickq.reservation.CreateReservationRequest
import com.martdev.flickq.shared.api.AUTH_JWT
import com.martdev.flickq.shared.api.DataResponse
import com.martdev.flickq.shared.api.withRole
import com.martdev.flickq.shared.util.extractUserId
import com.martdev.shared.api.getLimitAndOffset
import com.martdev.shared.api.getParameterFromPath
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

const val reservationPath = "/reservation"
const val adminReservationPath = "/admin$reservationPath"

fun Route.reservationRoute() {
    val reservationService by inject<ReservationService>()
    val showtimeSeatService by inject<ShowtimeSeatService>()
    val cancellationService by inject<ReservationCancellationService>()
    adminReservationRoutes(reservationService, showtimeSeatService, cancellationService)
    userReservationRoutes(reservationService, showtimeSeatService)
}

private fun Route.adminReservationRoutes(
    reservationService: ReservationService,
    showtimeSeatService: ShowtimeSeatService,
    cancellationService: ReservationCancellationService,
) {
    authenticate(AUTH_JWT) {
        withRole(Role.ADMIN) {
            route(adminReservationPath) {

                // Must be called after creating a showtime to populate its seat inventory
                post("/populate-seats/{showtime_id}") {
                    val showtimeId = getParameterFromPath("showtime_id")
                    showtimeSeatService.populateShowtimeSeats(showtimeId)
                    call.respond(HttpStatusCode.Created,
                        DataResponse("Seats populated successfully")
                    )
                }

                get("/get-all") {
                    val (limit, offset) = getLimitAndOffset()
                    val result = reservationService.getAllReservations(limit, offset)
                        .map { it.toReservationDTO() }
                    call.respond(HttpStatusCode.OK, DataResponse(result))
                }

                get("/get-by-id/{reservation_id}") {
                    val reservationId = getParameterFromPath("reservation_id")
                    val result = reservationService.getReservationById(reservationId).toReservationDTO()
                    call.respond(HttpStatusCode.OK, DataResponse(result))
                }

                patch("/cancel/{reservation_id}") {
                    val reservationId = getParameterFromPath("reservation_id")
                    val result = cancellationService.cancelByAdmin(reservationId).toReservationDTO()
                    call.respond(HttpStatusCode.OK, DataResponse(result))
                }
            }
        }
    }
}

private fun Route.userReservationRoutes(
    reservationService: ReservationService,
    showtimeSeatService: ShowtimeSeatService
) {
    authenticate(AUTH_JWT) {
        route(reservationPath) {

            post("/create") {
                val userId = call.extractUserId()
                val request = call.receive<CreateReservationRequest>()
                val result = reservationService
                    .createReservation(userId, request.showtimeId, request.seatIds)
                    .toReservationDTO()
                call.respond(HttpStatusCode.Created, DataResponse(result))
            }

            get("/my-reservations") {
                val userId = call.extractUserId()
                val result = reservationService.getMyReservations(userId)
                    .map { it.toReservationDTO() }
                call.respond(HttpStatusCode.OK, DataResponse(result))
            }

            get("/{reservation_id}") {
                val reservationId = getParameterFromPath("reservation_id")
                val userId = call.extractUserId()
                val result = reservationService
                    .getMyReservationById(reservationId, userId)
                    .toReservationDTO()
                call.respond(HttpStatusCode.OK, DataResponse(result))
            }

            // Public: show available seats for a showtime (no auth needed — move outside authenticate if desired)
            get("/available-seats/{showtime_id}") {
                val showtimeId = getParameterFromPath("showtime_id")
                val result = showtimeSeatService.getAvailableSeats(showtimeId)
                    .map { it.toShowtimeSeatDTO() }
                call.respond(HttpStatusCode.OK, DataResponse(result))
            }

            patch("/cancel/{reservation_id}") {
                val reservationId = getParameterFromPath("reservation_id")
                val userId = call.extractUserId()
                val result = reservationService.cancelReservation(reservationId, userId).toReservationDTO()
                call.respond(HttpStatusCode.OK, DataResponse(result))
            }
        }
    }
}