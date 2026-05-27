package com.martdev.flickq.reservation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class ShowtimeSeatDTO(
    val id: Long = 0,
    @SerialName("showtime_id") val showtimeId: Long = 0,
    @SerialName("seat_id") val seatId: Long = 0,
    @SerialName("reservation_id") val reservationId: Long? = null,
    val status: String = ""
)

@Serializable
data class ReservationDTO(
    val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("showtime_id") val showtimeId: Long = 0,
    val status: String = "",
    @SerialName("total_amount") val totalAmount: Long = 0,
    val seats: List<ShowtimeSeatDTO> = emptyList(),
    @SerialName("created_at") val createdAt: Instant = Clock.System.now(),
    @SerialName("expires_at") val expiresAt: Instant = Clock.System.now()
)

// Request body for POST /reservation/create
// totalAmount is NOT included — server calculates it from showtime.price * seats.size
@Serializable
data class CreateReservationRequest(
    @SerialName("showtime_id") val showtimeId: Long,
    @SerialName("seat_ids") val seatIds: List<Long>
)