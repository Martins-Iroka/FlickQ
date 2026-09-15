package com.martdev.flickq.shared.api

import com.martdev.flickq.shared.domain.exception.BadRequestException
import io.ktor.server.routing.RoutingContext
import io.ktor.server.util.getValue

fun RoutingContext.getParameterFromPath(parameter: String): Long {
    return call.parameters[parameter]?.toLongOrNull() ?: throw BadRequestException("Invalid id")
}

fun RoutingContext.getLimitAndOffset(): Pair<Int, Long> {
    val limit: Int by call.queryParameters
    val offset: Long by call.queryParameters
    if (limit <= 0 || offset < 0) {
        throw BadRequestException("'limit' and 'offset' must be positive")
    }

    return Pair(limit, offset)
}