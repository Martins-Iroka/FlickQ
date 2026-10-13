package com.martdev.flickq.features.notification.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.notification.domain.service.OutboxAdminService
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.shared.DataResponse
import com.martdev.flickq.shared.api.AUTH_JWT
import com.martdev.flickq.shared.api.getLimitAndOffset
import com.martdev.flickq.shared.api.getParameterFromPath
import com.martdev.flickq.shared.api.withRole
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.util.extractUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

const val adminOutboxPath = "/admin/notifications/outbox"

fun Route.outboxAdminRoute() {
    val service by inject<OutboxAdminService>()
    authenticate(AUTH_JWT) {
        withRole(Role.ADMIN) {
            route(adminOutboxPath) {
                get {
                    val status = parseStatus(call.request.queryParameters["status"])
                    val (limit, offset) = getLimitAndOffset()
                    val rows = service.list(status, limit, offset)
                    call.respond(HttpStatusCode.OK, DataResponse(rows.map { it.toDTO() }))
                }
                post("/{id}/retry") {
                    val id = getParameterFromPath("id")
                    service.retry(id, call.extractUserId())
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun parseStatus(raw: String?): NotificationStatus? = raw?.let {
    NotificationStatus.entries.firstOrNull { s -> s.name.equals(it, ignoreCase = true) }
        ?: throw BadRequestException("Invalid status: $it")
}