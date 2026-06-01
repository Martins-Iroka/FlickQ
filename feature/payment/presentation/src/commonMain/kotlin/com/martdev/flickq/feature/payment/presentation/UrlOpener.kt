package com.martdev.flickq.feature.payment.presentation

import org.koin.core.module.Module

/**
 * Opens an external URL (the Paystack `authorization_url`) using the platform's in-app
 * browser: Android Custom Tabs, iOS Safari, web a new browser tab. After the hand-off the
 * [PaymentViewModel] polls `verify/{reference}` to learn the outcome, so the opener is
 * fire-and-forget — it does not report when the user returns.
 */
interface UrlOpener {
    fun open(url: String)
}

/** Per-platform Koin bindings for [UrlOpener]; mirrors `platformDataModule()`. */
expect fun paymentPlatformModule(): Module
