package com.martdev.flickq.features.report.api

import com.martdev.flickq.report.CapacityReportDTO
import com.martdev.flickq.report.CapacityRowDTO
import com.martdev.flickq.report.RevenueBucketDTO
import com.martdev.flickq.report.RevenueReportDTO
import com.martdev.flickq.report.model.CapacityReport
import com.martdev.flickq.report.model.CapacityRow
import com.martdev.flickq.report.model.RevenueBucket
import com.martdev.flickq.report.model.RevenueReport

fun RevenueBucket.toDTO() = RevenueBucketDTO(
    bucketStart = bucketStart,
    gross = gross,
    refunds = refunds,
    net = net,
    ticketsSold = ticketsSold,
)

fun RevenueReport.toDTO() = RevenueReportDTO(
    from = from,
    to = to,
    bucket = bucket.name,
    currency = currency,
    buckets = buckets.map { it.toDTO() },
    totalGross = totalGross,
    totalRefunds = totalRefunds,
    totalNet = totalNet,
    totalTicketsSold = totalTicketsSold,
)

fun CapacityRow.toDTO() = CapacityRowDTO(
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

fun CapacityReport.toDTO() = CapacityReportDTO(
    from = from,
    to = to,
    rows = rows.map { it.toDTO() },
    totalShowtimes = totalShowtimes,
    avgOccupancyRate = avgOccupancyRate,
    totalSeatsBooked = totalSeatsBooked,
    totalSeatsTotal = totalSeatsTotal,
)
