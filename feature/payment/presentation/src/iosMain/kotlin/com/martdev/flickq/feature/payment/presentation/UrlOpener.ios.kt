package com.martdev.flickq.feature.payment.presentation

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Opens the checkout in Safari via the system. (SFSafariViewController would keep it in-app
 * but needs a presenting controller; the polling model means an external browser works too.)
 */
class IosUrlOpener : UrlOpener {
    override fun open(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}

actual fun paymentPlatformModule(): Module = module {
    single<UrlOpener> { IosUrlOpener() }
}
