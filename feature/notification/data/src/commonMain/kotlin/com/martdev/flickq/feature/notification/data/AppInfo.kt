package com.martdev.flickq.feature.notification.data

import com.martdev.flickq.notification.model.DevicePlatform
import org.koin.core.module.Module

/**
 * What `PUT /devices` reports about the app. [platform] is `null` where the server has no
 * platform (web, JVM): those targets never register.
 */
data class AppInfo(
    val platform: DevicePlatform?,
    val appVersion: String,
    val locale: String,
)

/**
 * Normalizes raw platform values to what the server's request validation accepts, so a quirky
 * device locale or version string can't turn every registration into a 400.
 */
object AppInfoSanitizer {

    const val FALLBACK_LOCALE = "en"
    const val FALLBACK_APP_VERSION = "unknown"
    private const val MAX_APP_VERSION_LENGTH = 32
    private const val MAX_LOCALE_SUBTAGS = 4
    private const val MAX_LOCALE_LENGTH = 35

    private val languagePattern = Regex("^[A-Za-z]{2,3}$")
    private val subtagPattern = Regex("^[A-Za-z0-9]{1,8}$")

    /** Trimmed and capped at 32 characters (the server accepts 1–32). */
    fun appVersion(raw: String?): String =
        raw?.trim()?.take(MAX_APP_VERSION_LENGTH)?.trimEnd()?.takeIf { it.isNotEmpty() }
            ?: FALLBACK_APP_VERSION

    /**
     * Fits `^[A-Za-z]{2,3}([-_][A-Za-z0-9]{1,8}){0,4}$` within 35 characters: drops iOS
     * `@calendar=…` keywords, malformed subtags, anything past four subtags and trailing subtags
     * over the length cap; falls back to `en` when the language itself is unusable.
     */
    fun locale(raw: String?): String {
        val parts = raw?.trim()?.substringBefore('@')?.split('-', '_').orEmpty()
        val language = parts.firstOrNull()?.takeIf { languagePattern.matches(it) }
            ?: return FALLBACK_LOCALE
        var tags = listOf(language) + parts.drop(1)
            .filter { subtagPattern.matches(it) }
            .take(MAX_LOCALE_SUBTAGS)
        while (tags.joinToString("-").length > MAX_LOCALE_LENGTH) tags = tags.dropLast(1)
        return tags.joinToString("-")
    }
}

/** Per-platform Koin binding for [AppInfo]; mirrors `platformDataModule()`. */
expect fun notificationDataPlatformModule(): Module