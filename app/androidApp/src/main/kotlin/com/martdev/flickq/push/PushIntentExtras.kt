package com.martdev.flickq.push

import android.content.Intent
import com.martdev.flickq.feature.notification.domain.PushDeepLinkParser

/**
 * The payload keys a notification tap carries into [com.martdev.flickq.MainActivity], whether
 * the system tray built the launch intent (background) or [PushNotificationPresenter] did
 * (foreground). Only these keys are ever read or forwarded.
 */
object PushIntentExtras {
    val KEYS = listOf(
        PushDeepLinkParser.KEY_DEEPLINK,
        PushDeepLinkParser.KEY_TYPE,
        PushDeepLinkParser.KEY_RESERVATION_ID,
        PushDeepLinkParser.KEY_MOVIE_ID,
    )

    /** The push keys on [intent], or `null` if it carries none. */
    fun read(intent: Intent?): Map<String, String?>? {
        val extras = intent?.extras ?: return null
        val data = KEYS.associateWith { extras.getString(it) }
        return data.takeIf { values -> values.values.any { it != null } }
    }

    /** Removes the keys so a re-delivered or recreated intent doesn't navigate twice. */
    fun strip(intent: Intent) {
        KEYS.forEach(intent::removeExtra)
    }
}