package com.martdev.flickq.feature.notification.domain

/** The signed-in identity, as far as push registration cares. */
data class PushSession(
    val userId: String,
    /** Only the `USER` role may register devices; an ADMIN token gets a 403. */
    val isCustomer: Boolean,
)

fun interface PushSessionProvider {
    /** The current session, or `null` when signed out. */
    suspend fun current(): PushSession?
}