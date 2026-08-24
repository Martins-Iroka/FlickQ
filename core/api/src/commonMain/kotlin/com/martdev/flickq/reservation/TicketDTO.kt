package com.martdev.flickq.reservation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class TicketDTO(
    val status: String = "",
    @SerialName("total_amount")
    val totalAmount: Long = 0,
    @SerialName("expires_at")
    val expiresAt: Instant = Clock.System.now(),
    @SerialName("starts_at")
    val startsAt: Instant = Clock.System.now(),
    @SerialName("ends_at")
    val endsAt: Instant = Clock.System.now(),
    @SerialName("movie_title")
    val movieTitle: String = "",
    @SerialName("poster_url")
    val posterUrl: String = "",
    @SerialName("room_name")
    val roomName: String = "",
    val seats: List<String> = emptyList(),
    val payment: TicketPaymentDTO? = null
)

@Serializable
data class TicketPaymentDTO(
    val status: String = "",
    val reference: String = "",
    @SerialName("paid_at")
    val paidAt: Instant? = null
)
