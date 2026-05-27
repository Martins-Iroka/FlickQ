package com.martdev.flickq.features.report.domain.service

import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueReport
import kotlin.time.Instant

interface ReportService {
    suspend fun getRevenueReport(
        from: Instant,
        to: Instant,
        bucket: ReportBucketGranularity,
    ): RevenueReport

    suspend fun getCapacityReport(
        from: Instant,
        to: Instant,
        limit: Int,
        offset: Long,
        movieId: Long?,
        roomId: Long?,
    ): CapacityReport
}
