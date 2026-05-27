package com.martdev.flickq.report

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class RevenueBucketDTO(
    @SerialName("bucket_start") val bucketStart: Instant,
    val gross: Long,
    val refunds: Long,
    val net: Long,
    @SerialName("tickets_sold") val ticketsSold: Long,
)

@Serializable
data class RevenueReportDTO(
    val from: Instant,
    val to: Instant,
    val bucket: String,
    val currency: String,
    val buckets: List<RevenueBucketDTO>,
    @SerialName("total_gross") val totalGross: Long,
    @SerialName("total_refunds") val totalRefunds: Long,
    @SerialName("total_net") val totalNet: Long,
    @SerialName("total_tickets_sold") val totalTicketsSold: Long,
)

@Serializable
data class CapacityRowDTO(
    @SerialName("showtime_id") val showtimeId: Long,
    @SerialName("movie_id") val movieId: Long,
    @SerialName("movie_title") val movieTitle: String,
    @SerialName("room_id") val roomId: Long,
    @SerialName("room_name") val roomName: String,
    @SerialName("starts_at") val startsAt: Instant,
    @SerialName("ends_at") val endsAt: Instant,
    @SerialName("seats_total") val seatsTotal: Int,
    @SerialName("seats_booked") val seatsBooked: Int,
    @SerialName("seats_held") val seatsHeld: Int,
    @SerialName("seats_available") val seatsAvailable: Int,
    @SerialName("occupancy_rate") val occupancyRate: Double,
)

@Serializable
data class CapacityReportDTO(
    val from: Instant,
    val to: Instant,
    val rows: List<CapacityRowDTO>,
    @SerialName("total_showtimes") val totalShowtimes: Long,
    @SerialName("avg_occupancy_rate") val avgOccupancyRate: Double,
    @SerialName("total_seats_booked") val totalSeatsBooked: Long,
    @SerialName("total_seats_total") val totalSeatsTotal: Long,
)
