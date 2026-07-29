package com.martdev.flickq.feature.payment.presentation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module


class AndroidPaystackUrl : UrlOpener {
    private var terminalStatusChecked = false
    private var awaitingRedirect: Boolean = false
    private var cancelCallback: (() -> Unit)? = null
    private var resultCallback : (() -> Unit)? = null

    private var checkoutLauncher: ActivityResultLauncher<Intent>? = null
    private var activity: ComponentActivity? = null

    override fun launchCheckout(
        checkoutUrl: String,
        callbackScheme: String,
        onCancel: () -> Unit,
        onResult: () -> Unit
    ) {
//        terminalStatusChecked = false
        awaitingRedirect = true
        cancelCallback = onCancel
        resultCallback = onResult

        /*val customTabs = CustomTabsIntent.Builder().build()
        val intent = customTabs.intent
        intent.data = checkoutUrl.toUri()*/
        CustomTabsIntent.Builder().build().launchUrl(activity!!, checkoutUrl.toUri())

//        checkoutLauncher?.launch(intent)
    }

    override fun handleRedirect() {
        println("HandleRedirect called")
        if (!awaitingRedirect) return
//        terminalStatusChecked = true
        awaitingRedirect = false
        resultCallback?.invoke()
    }

    fun register(activity: ComponentActivity) {
        this.activity = activity

        /*checkoutLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { r ->
            if (!terminalStatusChecked) {
                cancelCallback?.invoke()
            }
        }*/
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
