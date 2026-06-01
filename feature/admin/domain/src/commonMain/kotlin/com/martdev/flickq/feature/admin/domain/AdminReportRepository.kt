package com.martdev.flickq.feature.admin.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueReport
import kotlin.time.Instant

/**
 * Admin reporting over the `admin/reports` endpoints (ADMIN-gated on the server). Catalog,
 * reservation and payment admin operations live in their own repositories, added incrementally.
 */
interface AdminReportRepository {
    suspend fun getRevenueReport(
        from: Instant,
        to: Instant,
        bucket: ReportBucketGranularity = ReportBucketGranularity.DAY,
    ): Result<RevenueReport, DataError>

    suspend fun getCapacityReport(
        from: Instant,
        to: Instant,
        limit: Int = 50,
        offset: Int = 0,
        movieId: Long? = null,
        roomId: Long? = null,
    ): Result<CapacityReport, DataError>
}
