package com.martdev.flickq.shared.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.shared.domain.exception.ForbiddenException
import com.martdev.flickq.shared.util.extractRole
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

const val AUTH_JWT = "auth-jwt"

fun rolePlugin(requiredRole: Role) = createRouteScopedPlugin(
    name = "RolePlugin-${requiredRole.name}"
) {
    on(AuthenticationChecked) { call ->
        if (call.extractRole() != requiredRole) throw ForbiddenException()
    }
}

fun Route.withRole(requiredRole: Role, build: Route.() -> Unit) {
    install(rolePlugin(requiredRole))
    build()
}