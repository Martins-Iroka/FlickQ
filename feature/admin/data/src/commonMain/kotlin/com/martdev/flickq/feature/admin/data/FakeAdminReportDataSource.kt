package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.admin.domain.AdminReportRepository
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.CapacityRow
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueBucket
import com.martdev.flickq.report.model.RevenueReport
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * In-memory sample reports so the admin dashboard renders on fakes. Swapped for
 * [RealAdminReportDataSource] when wiring the real API.
 */
class FakeAdminReportDataSource : AdminReportRepository {

    override suspend fun getRevenueReport(
        from: kotlin.time.Instant,
        to: kotlin.time.Instant,
        bucket: ReportBucketGranularity,
    ): Result<RevenueReport, DataError> {
        val buckets = (0 until 7).map { day ->
            val gross = 120_000L + day * 15_000L
            val refunds = day * 2_000L
            RevenueBucket(
                bucketStart = from + day.days,
                gross = gross,
                refunds = refunds,
                net = gross - refunds,
                ticketsSold = 34L + day * 4L,
            )
        }
        return Result.Success(
            RevenueReport(
                from = from,
                to = to,
                bucket = bucket,
                currency = "NGN",
                buckets = buckets,
                totalGross = buckets.sumOf { it.gross },
                totalRefunds = buckets.sumOf { it.refunds },
                totalNet = buckets.sumOf { it.net },
                totalTicketsSold = buckets.sumOf { it.ticketsSold },
            )
        )
    }

    override suspend fun getCapacityReport(
        from: kotlin.time.Instant,
        to: kotlin.time.Instant,
        limit: Int,
        offset: Int,
        movieId: Long?,
        roomId: Long?,
    ): Result<CapacityReport, DataError> {
        val now = Clock.System.now()
        val rows = listOf(
            CapacityRow(1, 1, "Neon Skyline", 1, "Screen 1", now, now + 2.days, 80, 64, 4, 12, 0.80),
            CapacityRow(2, 2, "The Quiet Coast", 2, "Screen 2", now, now + 2.days, 80, 30, 2, 48, 0.375),
            CapacityRow(3, 4, "Last Call at the Atlas", 3, "Screen 3", now, now + 2.days, 60, 58, 0, 2, 0.967),
        )
        return Result.Success(
            CapacityReport(
                from = from,
                to = to,
                rows = rows,
                totalShowtimes = rows.size.toLong(),
                avgOccupancyRate = rows.map { it.occupancyRate }.average(),
                totalSeatsBooked = rows.sumOf { it.seatsBooked.toLong() },
                totalSeatsTotal = rows.sumOf { it.seatsTotal.toLong() },
            )
        )
    }
}
