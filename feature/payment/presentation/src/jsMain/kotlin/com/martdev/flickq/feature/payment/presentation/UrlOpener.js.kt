package com.martdev.flickq.feature.payment.presentation

import org.koin.core.module.Module
import org.koin.dsl.module

/** Opens the checkout in a new browser tab; the app tab keeps polling for the outcome. */
class WebUrlOpener : UrlOpener {
    override fun open(url: String) {
        openInNewTab(url)
    }
}

private fun openInNewTab(url: String): Unit = js("window.open(url, '_blank')")

actual fun paymentPlatformModule(): Module = module {
    single<UrlOpener> { WebUrlOpener() }
}
