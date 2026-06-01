package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.report.CapacityReportDTO
import com.martdev.flickq.report.CapacityRowDTO
import com.martdev.flickq.report.RevenueBucketDTO
import com.martdev.flickq.report.RevenueReportDTO
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.CapacityRow
import com.martdev.flickq.report.model.ReportBucketGranularity
import com.martdev.flickq.report.model.RevenueBucket
import com.martdev.flickq.report.model.RevenueReport

/** Maps the free-string `bucket` from the server, defaulting unknown values to DAY. */
internal fun String.toBucketGranularity(): ReportBucketGranularity = when (uppercase()) {
    "WEEK" -> ReportBucketGranularity.WEEK
    "MONTH" -> ReportBucketGranularity.MONTH
    else -> ReportBucketGranularity.DAY
}

internal fun RevenueBucketDTO.toDomain(): RevenueBucket = RevenueBucket(
    bucketStart = bucketStart,
    gross = gross,
    refunds = refunds,
    net = net,
    ticketsSold = ticketsSold,
)

internal fun RevenueReportDTO.toDomain(): RevenueReport = RevenueReport(
    from = from,
    to = to,
    bucket = bucket.toBucketGranularity(),
    currency = currency,
    buckets = buckets.map { it.toDomain() },
    totalGross = totalGross,
    totalRefunds = totalRefunds,
    totalNet = totalNet,
    totalTicketsSold = totalTicketsSold,
)

internal fun CapacityRowDTO.toDomain(): CapacityRow = CapacityRow(
    showtimeId = showtimeId,
    movieId = movieId,
    movieTitle = movieTitle,
    roomId = roomId,
    roomName = roomName,
    startsAt = startsAt,
    endsAt = endsAt,
    seatsTotal = seatsTotal,
    seatsBooked = seatsBooked,
    seatsHeld = seatsHeld,
    seatsAvailable = seatsAvailable,
    occupancyRate = occupancyRate,
)

internal fun CapacityReportDTO.toDomain(): CapacityReport = CapacityReport(
    from = from,
    to = to,
    rows = rows.map { it.toDomain() },
    totalShowtimes = totalShowtimes,
    avgOccupancyRate = avgOccupancyRate,
    totalSeatsBooked = totalSeatsBooked,
    totalSeatsTotal = totalSeatsTotal,
)
