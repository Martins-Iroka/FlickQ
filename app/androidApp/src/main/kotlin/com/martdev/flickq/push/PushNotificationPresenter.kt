package com.martdev.flickq.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.RemoteMessage
import com.martdev.flickq.MainActivity
import com.martdev.flickq.R
import com.martdev.flickq.feature.notification.domain.PushDeepLinkParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Shows a notification message that arrived while the app was in the foreground (in the
 * background, the FCM SDK posts it to the tray itself). Payload text is shown as plain text only.
 */
internal class PushNotificationPresenter(private val context: Context) {

    @SuppressLint("MissingPermission") // checked in canPost()
    fun show(message: RemoteMessage) {
        val notification = message.notification ?: return
        val manager = NotificationManagerCompat.from(context)
        if (!canPost(manager)) return

        val data = message.data
        val type = data[PushDeepLinkParser.KEY_TYPE]
        val channelId = notification.channelId?.takeIf(NotificationChannels::isKnown)
            ?: NotificationChannels.forType(type)
        // Same tag + id as an earlier copy (at-least-once delivery) replaces it instead of stacking.
        val tag = message.collapseKey ?: type ?: DEFAULT_TAG
        val id = (data[PushDeepLinkParser.KEY_RESERVATION_ID] ?: data[PushDeepLinkParser.KEY_MOVIE_ID])
            ?.hashCode() ?: 0
        val isBooking = channelId == NotificationChannels.BOOKINGS

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_flickq)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setAutoCancel(true)
            .setPriority(if (isBooking) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent(data, requestCode = 31 * tag.hashCode() + id))

        if (isBooking) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(channelId))
        }
        loadPoster(notification.imageUrl)?.let { poster ->
            builder.setLargeIcon(poster)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(poster).bigLargeIcon(null as Bitmap?))
        }

        try {
            manager.notify(tag, id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to show.
        }
    }

    private fun canPost(manager: NotificationManagerCompat): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    /** Explicit, immutable intent to our own activity carrying only the allowlisted keys. */
    private fun contentIntent(data: Map<String, String>, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            PushIntentExtras.KEYS.forEach { key -> data[key]?.let { putExtra(key, it) } }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun publicVersion(channelId: String) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_flickq)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(context.getString(R.string.notification_booking_public_title))
            .build()

    /** https only, bounded in time and size; any failure falls back to the text-only notification. */
    private fun loadPoster(uri: Uri?): Bitmap? {
        if (uri?.scheme != "https") return null
        return try {
            val connection = URL(uri.toString()).openConnection() as HttpsURLConnection
            connection.connectTimeout = IMAGE_TIMEOUT_MS
            connection.readTimeout = IMAGE_TIMEOUT_MS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val bytes = connection.inputStream.use { it.readAtMost(MAX_IMAGE_BYTES) } ?: return null
                decodeSampled(bytes)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            Log.w(TAG, "Notification image unavailable; showing text only")
            null
        }
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_WIDTH_PX) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private companion object {
        const val TAG = "PushNotification"
        const val DEFAULT_TAG = "flickq"
        // Connect + read ≈ the plan's 5 s budget.
        const val IMAGE_TIMEOUT_MS = 2_500
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        const val MAX_IMAGE_WIDTH_PX = 1024
    }
}