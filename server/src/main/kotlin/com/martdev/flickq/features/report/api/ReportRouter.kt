package com.martdev.flickq.features.report.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.features.report.domain.service.ReportService
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.shared.api.AUTH_JWT
import com.martdev.flickq.shared.api.DataResponse
import com.martdev.flickq.shared.api.withRole
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.shared.api.getLimitAndOffset
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import kotlin.time.Instant

const val adminReportPath = "/admin/reports"

fun Route.reportRoute() {
    val service by inject<ReportService>()
    authenticate(AUTH_JWT) {
        withRole(Role.ADMIN) {
            route(adminReportPath) {
                get("/revenue") {
                    val from = requireInstant("from")
                    val to = requireInstant("to")
                    val bucket = parseBucket(call.request.queryParameters["bucket"])
                    val report = service.getRevenueReport(from, to, bucket)
                    call.respond(HttpStatusCode.OK, DataResponse(report.toDTO()))
                }
                get("/capacity") {
                    val from = requireInstant("from")
                    val to = requireInstant("to")
                    val (limit, offset) = getLimitAndOffset()
                    val movieId = call.request.queryParameters["movieId"]?.toLongOrNull()
                    val roomId = call.request.queryParameters["roomId"]?.toLongOrNull()
                    val report = service.getCapacityReport(from, to, limit, offset, movieId, roomId)
                    call.respond(HttpStatusCode.OK, DataResponse(report.toDTO()))
                }
            }
        }
    }
}

private fun RoutingContext.requireInstant(name: String): Instant =
    call.request.queryParameters[name]
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: throw BadRequestException("Invalid or missing '$name' (expected ISO-8601 instant)")

private fun parseBucket(raw: String?): ReportBucketGranularity = when (raw?.uppercase()) {
    null, "DAY" -> ReportBucketGranularity.DAY
    "WEEK" -> ReportBucketGranularity.WEEK
    "MONTH" -> ReportBucketGranularity.MONTH
    else -> throw BadRequestException("Invalid bucket: $raw")
}
