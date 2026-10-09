package com.martdev.flickq.features.notification.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.notification.domain.service.DeviceService
import com.martdev.flickq.notification.request.RegisterDeviceRequestDTO
import com.martdev.flickq.shared.api.AUTH_JWT
import com.martdev.flickq.shared.api.withRole
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.util.extractUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

const val devicePath = "/devices"

fun Route.deviceRoute() {
    val deviceService by inject<DeviceService>()
    authenticate(AUTH_JWT) {
        withRole(Role.USER) {
            rateLimit(RateLimitName("devices")) {
                route(devicePath) {
                    put {
                        val userId = call.extractUserId()
                        val request = call.receive<RegisterDeviceRequestDTO>()
                            .toRegisterDeviceRequest()
                        deviceService.register(userId, request)
                        call.respond(HttpStatusCode.NoContent)
                    }

                    delete("/{token}") {
                        val userId = call.extractUserId()
                        val token = call.parameters["token"] ?: throw BadRequestException(
                            "Device token is required"
                        )
                        deviceService.unregister(userId, token)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}