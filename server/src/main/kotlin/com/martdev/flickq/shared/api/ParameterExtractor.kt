package com.martdev.flickq.shared.api

import com.martdev.flickq.shared.domain.exception.BadRequestException
import io.ktor.server.routing.RoutingContext

fun RoutingContext.getParameterFromPath(parameter: String): Long {
    return call.parameters[parameter]?.toLongOrNull() ?: throw BadRequestException("Invalid id")
}

fun RoutingContext.getLimitAndOffset(): Pair<Int, Long> {
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
    if (limit <= 0 || offset < 0) {
        throw BadRequestException("'limit' and 'offset' must be positive")
    }

    return Pair(limit, offset)
}