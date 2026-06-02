package com.martdev.flickq.core.data

import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Common data wiring: the shared [HttpClient] (engine + token-aware auth) and the
 * [SessionManager] the auth refresh path signals on expiry. The platform [TokenStorage] is
 * provided by [platformDataModule]; all are registered in `initKoin`.
 */
val coreDataModule = module {
    single { SessionManager() }
    single<HttpClient> { HttpClientFactory.create(httpClientEngine(), get(), get()) }
}
