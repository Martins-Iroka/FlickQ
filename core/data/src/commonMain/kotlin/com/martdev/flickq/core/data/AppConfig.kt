package com.martdev.flickq.core.data

import com.martdev.flickq.core.data.AppConfig.BASE_URL
import com.martdev.flickq.core.data.AppConfig.USE_FAKES


/**
 * Client-wide runtime configuration.
 *
 * [USE_FAKES] is the single switch that selects in-memory fakes vs. real Ktor data
 * sources — each feature's Koin `*DataModule` branches on it. Keep it `true` so the app
 * stays runnable offline; flip to `false` (and point [BASE_URL] at a reachable backend)
 * to exercise the real API. Android emulators must target `10.0.2.2`, not `localhost`.
 *
 * (Deliberately a plain object rather than a generated BuildKonfig: on this Kotlin/AGP
 * stack a plain const is zero-risk and equally effective for binding selection. It can be
 * promoted to BuildKonfig flavors later without touching call sites.)
 */
object AppConfig {
    const val USE_FAKES: Boolean = false

    const val BASE_URL: String = "https://finn-unsmitten-raeann.ngrok-free.dev/api/v1"

    /**
     * Scheme + host + port of [BASE_URL] with the path stripped (e.g. `http://localhost:8080`).
     * The web engines use it to scope the credentials shim to API calls only (see
     * `PlatformDataModule` js/wasmJs actuals) so cross-origin image/CDN loads stay untouched.
     */
    val BASE_ORIGIN: String = run {
        val schemeEnd = BASE_URL.indexOf("://")
        if (schemeEnd < 0) {
            BASE_URL
        } else {
            val pathStart = BASE_URL.indexOf('/', startIndex = schemeEnd + 3)
            if (pathStart < 0) BASE_URL else BASE_URL.substring(0, pathStart)
        }
    }
}
