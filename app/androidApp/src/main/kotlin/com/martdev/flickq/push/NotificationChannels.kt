package com.martdev.flickq.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.martdev.flickq.R
import com.martdev.flickq.notification.model.NotificationType

/** The two channels the server targets (`android.channelId`). The client never subscribes to topics. */
object NotificationChannels {
    const val BOOKINGS = "bookings"
    const val NEW_RELEASES = "new_releases"

    private val movieAddedType = NotificationType.MOVIE_ADDED.name.lowercase()

    fun isKnown(channelId: String): Boolean = channelId == BOOKINGS || channelId == NEW_RELEASES

    /** Fallback when a message names no (known) channel. */
    fun forType(type: String?): String = if (type == movieAddedType) NEW_RELEASES else BOOKINGS

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val bookings = NotificationChannel(
            BOOKINGS,
            context.getString(R.string.notification_channel_bookings_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_bookings_description)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val newReleases = NotificationChannel(
            NEW_RELEASES,
            context.getString(R.string.notification_channel_new_releases_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_new_releases_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(bookings, newReleases))
    }
}