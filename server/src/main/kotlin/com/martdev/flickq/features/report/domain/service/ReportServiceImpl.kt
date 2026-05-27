package com.martdev.flickq.features.report.domain.service

import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.features.report.domain.repository.ReportRepository
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueReport
import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.util.returnValue
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
class ReportServiceImpl(
    private val reportRepository: ReportRepository,
    private val paystackConfig: PaystackConfig,
) : ReportService {

    override suspend fun getRevenueReport(
        from: Instant,
        to: Instant,
        bucket: ReportBucketGranularity,
    ): RevenueReport {
        if (from >= to) throw BadRequestException("'from' must be before 'to'")

        val buckets = reportRepository.getRevenueBuckets(from, to, bucket).returnValue()
        val totalGross = buckets.sumOf { it.gross }
        val totalRefunds = buckets.sumOf { it.refunds }
        val totalTickets = buckets.sumOf { it.ticketsSold }

        return RevenueReport(
            from = from,
            to = to,
            bucket = bucket,
            currency = paystackConfig.currency,
            buckets = buckets,
            totalGross = totalGross,
            totalRefunds = totalRefunds,
            totalNet = totalGross - totalRefunds,
            totalTicketsSold = totalTickets,
        )
    }

    override suspend fun getCapacityReport(
        from: Instant,
        to: Instant,
        limit: Int,
        offset: Long,
        movieId: Long?,
        roomId: Long?,
    ): CapacityReport {
        if (from >= to) throw BadRequestException("'from' must be before 'to'")

        val rows = reportRepository.getCapacityRows(from, to, limit, offset, movieId, roomId).returnValue()
        val totals = reportRepository.getCapacityTotals(from, to, movieId, roomId).returnValue()
        val avgOccupancyRate =
            if (totals.totalTotal == 0L) 0.0 else totals.totalBooked.toDouble() / totals.totalTotal

        return CapacityReport(
            from = from,
            to = to,
            rows = rows,
            totalShowtimes = totals.totalShowtimes,
            avgOccupancyRate = avgOccupancyRate,
            totalSeatsBooked = totals.totalBooked,
            totalSeatsTotal = totals.totalTotal,
        )
    }
}
