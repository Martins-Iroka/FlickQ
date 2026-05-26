package com.martdev.flickq.report.model

import kotlin.time.Instant

data class CapacityRow(
    val showtimeId: Long,
    val movieId: Long,
    val movieTitle: String,
    val roomId: Long,
    val roomName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val seatsTotal: Int,
    val seatsBooked: Int,
    val seatsHeld: Int,
    val seatsAvailable: Int,
    val occupancyRate: Double,
)

data class CapacityReport(
    val from: Instant,
    val to: Instant,
    val rows: List<CapacityRow>,
    val totalShowtimes: Long,
    val avgOccupancyRate: Double,
    val totalSeatsBooked: Long,
    val totalSeatsTotal: Long,
)

data class CapacityTotals(
    val totalShowtimes: Long,
    val totalBooked: Long,
    val totalTotal: Long,
)
