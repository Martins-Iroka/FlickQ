package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import org.koin.core.annotation.Singleton
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Locale
import kotlin.time.Instant

@Singleton
class NotificationComposer(private val config: FcmConfig) {

    fun compose(event: NotificationEvent): OutboxDraft = when(event) {
        is NotificationEvent.BookingConfirmed -> OutboxDraft(
            type = NotificationType.BOOKING_CONFIRMED,
            idempotencyKey = "booking_confirmed:${event.reservationId}",
            targetKind = NotificationTargetKind.USER,
            userId = event.userId,
            push = PushMessage(
                title = "Booking confirmed",
                body = "Your tickets for ${event.movieTitle} are ready.",
                data = mapOf("type" to "booking_confirmed",
                    "reservationId" to "${event.reservationId}",
                    "startsAt" to event.startsAt.toString(),
                    "deeplink" to "flickq://ticket/${event.reservationId}"),
                highPriority = true,
                collapseKey = "reservation-${event.reservationId}",
                androidChannelId = "bookings"
            )
        )
        is NotificationEvent.MovieAdded -> OutboxDraft(
            type = NotificationType.MOVIE_ADDED,
            idempotencyKey = "movie_added:${event.movieId}",
            targetKind = NotificationTargetKind.TOPIC,
            topic = newMoviesTopic(),
            push = PushMessage(
                title = "New in cinemas",
                body = "${event.title} is now showing",
                data = mapOf("type" to "movie_added", "movieId" to "${event.movieId}",
                    "deeplink" to "flickq://movie/${event.movieId}"),
                collapseKey = "new-movies",
                imageUrl = event.posterUrl?.takeIf { it.startsWith("https://") },
                androidChannelId = "new_releases"
            )
        )
        is NotificationEvent.ReservationHeld -> OutboxDraft(
            type = NotificationType.RESERVATION_HELD,
            idempotencyKey = "reservation_held:${event.reservationId}",
            targetKind = NotificationTargetKind.USER,
            userId = event.userId,
            expiresAt = event.expiresAt,
            push = PushMessage(
                title = "Seats held",
                body = "${event.seats.joinToString(", ")} are held. Pay within 15 minutes.",
                data = mapOf(
                    "type" to "reservation_held",
                    "reservationId" to "${event.reservationId}",
                    "deeplink" to "flickq://ticket/${event.reservationId}"
                ),
                highPriority = true,
                collapseKey = "reservation-${event.reservationId}",
                androidChannelId = "bookings"
            )
        )
        is NotificationEvent.ReservationCancelled -> {
            val reason = event.reason.name.lowercase()
            val (title, body) = when (event.reason) {
                NotificationEvent.ReservationCancelled.Reason.SHOWTIME_CANCELLED ->
                    "Showtime cancelled" to "${event.movieTitle} has been cancelled. We'll be in touch about your booking."
                NotificationEvent.ReservationCancelled.Reason.ADMIN_CANCELLED ->
                    "Booking cancelled" to "Your booking for ${event.movieTitle} was cancelled. A refund has been initiated."
            }
            OutboxDraft(
                type = NotificationType.RESERVATION_CANCELLED,
                idempotencyKey = "reservation_cancelled:$reason:${event.reservationId}",
                targetKind = NotificationTargetKind.USER,
                userId = event.userId,
                push = PushMessage(
                    title = title,
                    body = body,
                    data = mapOf(
                        "type" to "reservation_cancelled",
                        "reason" to reason,
                        "reservationId" to "${event.reservationId}",
                        "showtimeId" to "${event.showtimeId}",
                        "startsAt" to event.startsAt.toString(),
                        "deeplink" to "flickq://ticket/${event.reservationId}"
                    ),
                    highPriority = true,
                    collapseKey = "reservation-${event.reservationId}",
                    androidChannelId = "bookings"
                )
            )
        }
        is NotificationEvent.PaymentRefunded -> {
            val amount = String.format(Locale.US, "%s %,.2f", event.currency, BigDecimal.valueOf(event.amount, 2))
            val body = when (event.reason) {
                NotificationEvent.PaymentRefunded.Reason.RESERVATION_CANCELLED ->
                    "Your seat hold ended before your payment came through, so we've started a refund of $amount."
                NotificationEvent.PaymentRefunded.Reason.DUPLICATE_CHARGE ->
                    "You were charged twice. Your booking is still confirmed and we've started a refund of the extra $amount."
            }
            OutboxDraft(
                type = NotificationType.PAYMENT_REFUNDED,
                idempotencyKey = "payment_refunded:${event.paymentId}",
                targetKind = NotificationTargetKind.USER,
                userId = event.userId,
                push = PushMessage(
                    title = "Refund on its way",
                    body = body,
                    data = mapOf(
                        "type" to "payment_refunded",
                        "reason" to event.reason.name.lowercase(),
                        "reservationId" to "${event.reservationId}",
                        "deeplink" to "flickq://ticket/${event.reservationId}"
                    ),
                    highPriority = true,
                    androidChannelId = "bookings"
                )
            )
        }
    }

    fun subscribeToNewMovies(token: String, now: Instant) = topicChange(
        NotificationType.TOPIC_SUBSCRIBE, "sub", token, now
    )

    fun unsubscribeFromNewMovies(token: String, now: Instant) = topicChange(
        NotificationType.TOPIC_UNSUBSCRIBE, "unsub", token, now
    )

    private fun topicChange(
        type: NotificationType,
        prefix: String,
        token: String,
        now: Instant
    ) = OutboxDraft(
        type = type,
        idempotencyKey = "$prefix:${fingerprint(token)}:${newMoviesTopic()}:${now.epochSeconds / 86_400}",
        targetKind = NotificationTargetKind.TOKENS,
        topic = newMoviesTopic(),
        pendingTokens = listOf(token),
        push = PushMessage(title = "", body = "")
    )

    private fun newMoviesTopic() = TopicNames.newMovies(config.topicPrefix)

    private fun fingerprint(token: String) = MessageDigest.getInstance(
        "SHA-256"
    ).digest(token.toByteArray()).joinToString("") {
        "%02x".format(it)
    }.take(16)
}