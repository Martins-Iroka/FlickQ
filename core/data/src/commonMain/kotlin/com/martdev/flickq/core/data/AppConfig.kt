package com.martdev.flickq.core.data

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
    const val USE_FAKES: Boolean = true

    const val BASE_URL: String = "http://localhost:8080/api/v1"
}
