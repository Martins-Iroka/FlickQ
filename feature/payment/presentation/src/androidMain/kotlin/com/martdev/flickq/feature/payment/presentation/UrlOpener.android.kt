package com.martdev.flickq.feature.payment.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Opens the checkout in a Chrome Custom Tab. Launched from the application context, so the
 * tab needs its own task.
 */
class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override fun open(url: String) {
        val customTabs = CustomTabsIntent.Builder().build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabs.launchUrl(context, Uri.parse(url))
    }
}

actual fun paymentPlatformModule(): Module = module {
    single<UrlOpener> { AndroidUrlOpener(androidContext()) }
}
