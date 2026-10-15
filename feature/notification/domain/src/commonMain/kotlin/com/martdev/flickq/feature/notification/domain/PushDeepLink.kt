package com.martdev.flickq.feature.notification.domain

/** Where a notification tap may take the user. Nothing outside this allowlist is navigable. */
sealed interface PushDeepLink {
    data class Ticket(val reservationId: Long) : PushDeepLink
    data class Movie(val movieId: Long) : PushDeepLink
}

/**
 * Turns a push payload's string `data` map into a [PushDeepLink]. Payload data is untrusted:
 * - `deeplink` must be exactly `flickq://ticket/{id}` or `flickq://movie/{id}` with a positive
 *   numeric id; anything else (other schemes or hosts, extra segments, queries) yields `null`.
 * - Only when `deeplink` is absent does it fall back to `type` plus `reservationId`/`movieId`.
 */
object PushDeepLinkParser {

    const val KEY_DEEPLINK = "deeplink"
    const val KEY_TYPE = "type"
    const val KEY_RESERVATION_ID = "reservationId"
    const val KEY_MOVIE_ID = "movieId"

    private const val TYPE_BOOKING_CONFIRMED = "booking_confirmed"
    private const val TYPE_RESERVATION_HELD = "reservation_held"
    private const val TYPE_MOVIE_ADDED = "movie_added"

    private const val HOST_TICKET = "ticket"
    private const val HOST_MOVIE = "movie"

    private val linkPattern = Regex("^flickq://($HOST_TICKET|$HOST_MOVIE)/([1-9][0-9]{0,18})$")
    private val idPattern = Regex("^[1-9][0-9]{0,18}$")

    fun parse(data: Map<String, String?>): PushDeepLink? {
        val link = data[KEY_DEEPLINK]
        if (link != null) return parseLink(link)
        return when (data[KEY_TYPE]) {
            TYPE_BOOKING_CONFIRMED, TYPE_RESERVATION_HELD ->
                parseId(data[KEY_RESERVATION_ID])?.let { PushDeepLink.Ticket(it) }
            TYPE_MOVIE_ADDED -> parseId(data[KEY_MOVIE_ID])?.let { PushDeepLink.Movie(it) }
            else -> null
        }
    }

    fun parseLink(link: String): PushDeepLink? {
        val match = linkPattern.matchEntire(link) ?: return null
        val id = parseId(match.groupValues[2]) ?: return null
        return when (match.groupValues[1]) {
            HOST_TICKET -> PushDeepLink.Ticket(id)
            HOST_MOVIE -> PushDeepLink.Movie(id)
            else -> null
        }
    }

    // The regex caps the length at 19 digits; toLongOrNull rejects what still overflows a Long.
    private fun parseId(raw: String?): Long? =
        raw?.takeIf { idPattern.matches(it) }?.toLongOrNull()
}