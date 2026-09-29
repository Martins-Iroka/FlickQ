package com.martdev.flickq.features.notification.infrastructure.fcm

import com.google.api.client.json.gson.GsonFactory
import com.google.firebase.ErrorCode
import com.google.firebase.IncomingHttpResponse
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.TopicManagementResponse
import com.martdev.flickq.features.notification.domain.service.PushMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FirebaseMessageMapperTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")

    private val booking = PushMessage(
        title = "Booking confirmed",
        body = "Dune: Part Three · 19:30",
        data = mapOf(
            "type" to "BOOKING_CONFIRMED",
            "reservationId" to "42",
            "deeplink" to "flickq://ticket/42"
        ),
        highPriority = true,
        collapseKey = "reservation-42",
        ttl = 1.hours,
        androidChannelId = "bookings"
    )

    private fun Message.wire(): JsonObject = Json.parseToJsonElement(
        GsonFactory.getDefaultInstance().toString(this)
    ).jsonObject

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    @Nested
    inner class Targeting {
        @Test
        fun `token target sets token and no topic`() {
            val json = booking.toFirebase(FcmTarget.Token("tok-abc"), now).wire()
            assertEquals("tok-abc", json.str("fid"))
            assertNull(json["topic"])
        }

        @Test
        fun `topic target sets topic and no token`() {
            val json = booking.toFirebase(FcmTarget.Topic("dev-new-movies"), now).wire()

            assertEquals("dev-new-movies", json.str("topic"))
            assertNull(json["fid"])
        }
    }

    @Nested
    inner class NotificationAndData {
        @Test
        fun `title body and data are copied verbatim`() {
            val json = booking.toFirebase(FcmTarget.Topic("t"), now).wire()
            assertEquals("Booking confirmed", json.obj("notification").str("title"))
            assertEquals("Dune: Part Three · 19:30", json.obj("notification").str("body"))
            assertEquals(booking.data, json.obj("data").mapValues {
                it.value.jsonPrimitive.content
            })
        }

        @Test
        fun `reserved data keys are rejected before hitting FCM`() {
            listOf("from", "notification", "message_type", "google.sent_time", "gcm.n.e")
                .forEach { key ->
                    assertThrows<IllegalArgumentException>("key=$key") {
                        booking.copy(data = mapOf(key to "x")).toFirebase(FcmTarget.Token("t"), now)
                    }
                }
        }
    }

    @Nested
    inner class Android {
        @Test
        fun `high priority booking maps priority ttl collapse channel and tag`() {
            val android = booking.toFirebase(
                FcmTarget.Token("t"),
                now
            ).wire().obj("android")
            assertEquals("high", android.str("priority"))
            assertEquals("3600s", android.str("ttl"))
            assertEquals("reservation-42", android.str("collapse_key"))
            assertEquals("bookings", android.obj("notification").str("channel_id"))
            assertEquals("reservation-42", android.obj("notification").str("tag"))
        }

        @Test
        fun `normal priority and no ttl when not set`() {
            val movie = booking.copy(highPriority = false, ttl = null, collapseKey = null)
            val android =
                movie.toFirebase(FcmTarget.Topic("dev-new-movies"), now).wire().obj("android")
            assertEquals("normal", android.str("priority"))
            assertNull(android["ttl"])
            assertNull(android["collapse_key"])
        }
    }

    @Nested
    inner class Apns {
        @Test
        fun `high priority is 10 and expiration is now plus ttl in epoch seconds`() {
            val headers = booking.toFirebase(
                FcmTarget.Token("t"),
                now
            ).wire().obj("apns").obj("headers")
            assertEquals("10", headers.str("apns-priority"))
            assertEquals(now.plus(1.hours).epochSeconds.toString(),
                headers.str("apns-expiration"))
            assertEquals("reservation-42", headers.str("apns-collapse-id"))
        }

        @Test
        fun `normal priority is 5 and no expiration header without ttl`() {
            val headers = booking.copy(
                highPriority = false, ttl = null
            ).toFirebase(FcmTarget.Token("t"),
                now).wire().obj("apns").obj("headers")

            assertEquals("5", headers.str("apns-priority"))
            assertNull(headers["apns-expiration"])
        }

        @Test
        fun `image sets mutable-content and fcm_options image`() {
            val apns = booking.copy(imageUrl =
            "image_url").toFirebase(FcmTarget.Token("t"), now)
                .wire().obj("apns")

            assertEquals(1, apns.obj("payload").obj("aps")
                .getValue("mutable-content").jsonPrimitive.int)

            assertEquals("image_url", apns.obj("fcm_options").str("image"))
        }

        @Test
        fun `no image means no mutable-content`() {
            val aps = booking.toFirebase(
                FcmTarget.Token("t"),
                now
            ).wire().obj("apns").obj("payload").obj("aps")

            assertFalse(aps.containsKey("mutable-content"))
        }
    }

    @Nested
    inner class RetryAfter {
        @Test
        fun `delta seconds`() = assertEquals(120.seconds,
            parseRetryAfter("120", now))

        @Test
        fun `header value arrives as a list`() =
            assertEquals(30.seconds,
                parseRetryAfter(listOf("30"), now))

        @Test
        fun `http data in the future`() =
            assertEquals(5.minutes, parseRetryAfter(
                "Thu, 01 Oct 2026 12:05:00 GMT", now
            ))

        @Test
        fun `http date in the past clamps to zero`() = assertEquals(
            Duration.ZERO, parseRetryAfter("Thu, 01 Oct 2026 11:00:00 GMT", now)
        )

        @Test
        fun `garbage is ignored`() = assertNull(parseRetryAfter("null", now))
    }

    @Nested
    inner class ErrorMapping {
        @Test
        fun `quota exceeded carries status and retry-after`() {
            val response = mockk<IncomingHttpResponse> {
                every { statusCode } returns 429
                every { headers } returns mapOf(
                    "Retry-After" to listOf("45")
                )
            }
            val e = mockk<FirebaseMessagingException> {
                every { messagingErrorCode } returns MessagingErrorCode.QUOTA_EXCEEDED
                every { errorCode } returns ErrorCode.RESOURCE_EXHAUSTED
                every { httpResponse } returns response
                every { message } returns "quota"
            }
            val err = e.toFcmError(now)
            assertEquals("QUOTA_EXCEEDED", err.code)
            assertEquals(429, err.httpStatus)
            assertEquals(45.seconds, err.retryAfter)
        }

        @Test
        fun `falls back to platform error code when messaging code is null`() {
            val e = mockk<FirebaseMessagingException> {
                every { messagingErrorCode } returns null
                every { errorCode } returns ErrorCode.UNAVAILABLE
                every { httpResponse } returns null
                every { message } returns "down"
            }

            assertEquals("UNAVAILABLE", e.toFcmError(now).code)
            assertNull(e.toFcmError(now).retryAfter)
        }

        @Test
        fun `topic management reasons are normalized`() {
            mapOf(
                "registration-token-not-registered" to "UNREGISTERED",
                "invalid-argument" to "INVALID_ARGUMENT",
                "too-many-topics" to "TOO_MANY_TOPICS",
                "internal-error" to "INTERNAL",
                "unknown-error" to "INTERNAL"
            ).forEach { (reason, expected) ->
                val error = mockk<TopicManagementResponse.Error> {
                    every { this@mockk.reason } returns reason
                }
                assertEquals(expected, error.toFcmError().code, "reason=$reason")
            }
        }
    }
}