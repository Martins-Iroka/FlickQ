package com.martdev.flickq.feature.payment.presentation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module


class AndroidPaystackUrl : UrlOpener {
    private var terminalStatusChecked = false
    private var cancelCallback: (() -> Unit)? = null
    private var resultCallback : (() -> Unit)? = null

    private var checkoutLauncher: ActivityResultLauncher<Intent>? = null

    override fun launchCheckout(
        checkoutUrl: String,
        callbackScheme: String,
        onCancel: () -> Unit,
        onResult: () -> Unit
    ) {
        terminalStatusChecked = false
        cancelCallback = onCancel
        resultCallback = onResult

        val customTabs = CustomTabsIntent.Builder().build()
        val intent = customTabs.intent
        intent.data = checkoutUrl.toUri()

        checkoutLauncher?.launch(intent)
    }

    override fun handleRedirect() {
        println("HandleRedirect called")
        terminalStatusChecked = true
        resultCallback?.invoke()
    }

    fun register(activity: ComponentActivity) {
        checkoutLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { r ->
            if (!terminalStatusChecked) {
                cancelCallback?.invoke()
            }
        }
    }
}

actual fun paymentPlatformModule(): Module = module {
    single {
        AndroidPaystackUrl()
    } bind UrlOpener::class
}
