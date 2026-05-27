package com.martdev.features.report.domain.repository

import com.martdev.flickq.report.model.CapacityRow
import com.martdev.flickq.report.model.CapacityTotals
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueBucket
import com.martdev.flickq.shared.domain.model.DataResult
import kotlin.time.Instant

interface ReportRepository {
    suspend fun getRevenueBuckets(
        from: Instant,
        to: Instant,
        bucket: ReportBucketGranularity,
    ): DataResult<List<RevenueBucket>>

    suspend fun getCapacityRows(
        from: Instant,
        to: Instant,
        limit: Int,
        offset: Long,
        movieId: Long?,
        roomId: Long?,
    ): DataResult<List<CapacityRow>>

    suspend fun getCapacityTotals(
        from: Instant,
        to: Instant,
        movieId: Long?,
        roomId: Long?,
    ): DataResult<CapacityTotals>
}
