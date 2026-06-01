package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.feature.admin.domain.AdminReportRepository
import com.martdev.flickq.report.CapacityReportDTO
import com.martdev.flickq.report.RevenueReportDTO
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueReport
import io.ktor.client.HttpClient
import kotlin.time.Instant

/**
 * Ktor-backed admin reports. The Bearer token (an ADMIN JWT) is attached by the shared
 * [HttpClient]'s Auth plugin; the server enforces the role via `withRole(Role.ADMIN)`.
 * `from`/`to` are sent as ISO-8601 instants; null movie/room filters are omitted.
 */
class RealAdminReportDataSource(
    private val httpClient: HttpClient
) : AdminReportRepository {

    override suspend fun getRevenueReport(
        from: Instant,
        to: Instant,
        bucket: ReportBucketGranularity,
    ): Result<RevenueReport, DataError> =
        httpClient.getData<RevenueReportDTO>(
            route = "/admin/reports/revenue",
            queryParameters = mapOf(
                "from" to from.toString(),
                "to" to to.toString(),
                "bucket" to bucket.name,
            )
        ).map { it.toDomain() }

    override suspend fun getCapacityReport(
        from: Instant,
        to: Instant,
        limit: Int,
        offset: Int,
        movieId: Long?,
        roomId: Long?,
    ): Result<CapacityReport, DataError> =
        httpClient.getData<CapacityReportDTO>(
            route = "/admin/reports/capacity",
            queryParameters = mapOf(
                "from" to from.toString(),
                "to" to to.toString(),
                "limit" to limit,
                "offset" to offset,
                "movieId" to movieId,
                "roomId" to roomId,
            )
        ).map { it.toDomain() }
}
