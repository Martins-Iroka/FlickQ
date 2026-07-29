package com.martdev.flickq.feature.payment.presentation

import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module


class AndroidPaystackUrl : UrlOpener {
    private var awaitingRedirect: Boolean = false
    private var cancelCallback: (() -> Unit)? = null
    private var resultCallback : (() -> Unit)? = null

    private var activity: ComponentActivity? = null

    override fun launchCheckout(
        checkoutUrl: String,
        callbackScheme: String,
        onCancel: () -> Unit,
        onResult: () -> Unit
    ) {
        awaitingRedirect = true
        cancelCallback = onCancel
        resultCallback = onResult

        CustomTabsIntent.Builder().build().launchUrl(activity!!, checkoutUrl.toUri())
    }

    override fun handleRedirect() {
        println("HandleRedirect called")
        if (!awaitingRedirect) return
        awaitingRedirect = false
        resultCallback?.invoke()
    }

    fun register(activity: ComponentActivity) {
        this.activity = activity
    }

    fun unregister() {
        activity = null
    }

    fun onResume() {
        if (awaitingRedirect) {
            awaitingRedirect = false
            cancelCallback?.invoke()
        }
    }
}

actual fun paymentPlatformModule(): Module = module {
    single {
        AndroidPaystackUrl()
    } bind UrlOpener::class
}
