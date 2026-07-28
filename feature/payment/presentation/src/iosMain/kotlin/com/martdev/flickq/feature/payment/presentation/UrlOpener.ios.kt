package com.martdev.flickq.feature.payment.presentation

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL

class IosPaystackLauncher : UrlOpener {
    private var session: ASWebAuthenticationSession? = null

    override fun launchCheckout(
        checkoutUrl: String,
        callbackScheme: String,
        onCancel: () -> Unit,
        onResult: () -> Unit
    ) {
        val nsUrl = NSURL.URLWithString(checkoutUrl) ?: return

        session = ASWebAuthenticationSession(
            uRL = nsUrl,
            callbackURLScheme = callbackScheme,
            completionHandler = { callbackURL, error ->
                session = null

                if (error != null) {
                    println(error.localizedDescription)
                    onCancel()
                } else if (callbackURL != null) {
                    onResult()
                }
            }
        )

        // Configures presentation context (required for iOS 13+)
        session?.prefersEphemeralWebBrowserSession = true
        session?.start()
    }
}

actual fun paymentPlatformModule(): Module = module {
    factory<UrlOpener> { IosPaystackLauncher() }
}
