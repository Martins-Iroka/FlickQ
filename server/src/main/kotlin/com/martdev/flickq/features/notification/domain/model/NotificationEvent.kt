package com.martdev.flickq.features.notification.domain.model

import kotlin.time.Instant

sealed interface NotificationEvent {
    data class MovieAdded(val movieId: Long, val title: String, val posterUrl: String?) :
        NotificationEvent

    data class ReservationHeld(
        val reservationId: Long,
        val userId: Long,
        val seats: List<String>,
        val expiresAt: Instant
    ) : NotificationEvent

    data class BookingConfirmed(
        val reservationId: Long,
        val userId: Long,
        val movieTitle: String,
        val startsAt: Instant
    ) : NotificationEvent

    data class ReservationCancelled(
        val reservationId: Long,
        val userId: Long,
        val showtimeId: Long,
        val movieTitle: String,
        val startsAt: Instant,
        val reason: Reason
    ) : NotificationEvent {
        enum class Reason { SHOWTIME_CANCELLED, ADMIN_CANCELLED }
    }

    /** A refund the server started on its own; admin refunds are announced by [ReservationCancelled]. */
    data class PaymentRefunded(
        val paymentId: Long,
        val reservationId: Long,
        val userId: Long,
        val amount: Long,   // minor unit (kobo)
        val currency: String,
        val reason: Reason
    ) : NotificationEvent {
        enum class Reason { RESERVATION_CANCELLED, DUPLICATE_CHARGE }
    }
}