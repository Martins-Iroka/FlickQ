package com.martdev.flickq.features.notification.domain.model

import kotlin.time.Instant

sealed interface NotificationEvent {
    data class MovieAdded(val movieId: Long, val title: String, val posterUrl: String?) :
        NotificationEvent

    data class ReservationHeld(
        val reservationId: Long,
        val userId: Long,
        val movieTitle: String,
        val seats: List<String>,
        val expiresAt: Instant
    ) : NotificationEvent

    data class BookingConfirmed(
        val reservationId: Long,
        val userId: Long,
        val movieTitle: String,
        val startsAt: Instant
    ) : NotificationEvent
}