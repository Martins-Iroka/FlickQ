package com.martdev.flickq.feature.payment.presentation

import org.koin.core.module.Module
import org.koin.dsl.module

/** No-op opener for the JVM (used by unit tests; there is no JVM customer app). */
class NoopUrlOpener : UrlOpener {
    override fun open(url: String) = Unit
}

actual fun paymentPlatformModule(): Module = module {
    single<UrlOpener> { NoopUrlOpener() }
}
