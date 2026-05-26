package com.martdev.flickq.shared.util

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.shared.domain.exception.UnauthorizedException
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.extractUserId() = principal<JWTPrincipal>()
    ?.payload?.getClaim("userId")?.asString()?.toLongOrNull() ?: throw UnauthorizedException()

fun ApplicationCall.extractRole() = principal<JWTPrincipal>()
    ?.payload?.getClaim("role")?.asString()?.let { Role.valueOf(it) } ?: throw UnauthorizedException()