package com.martdev.flickq.core.data

import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Common data wiring: the shared [HttpClient] (engine + token-aware auth). The platform
 * [TokenStorage] is provided by [platformDataModule]; both are registered in `initKoin`.
 */
val coreDataModule = module {
    single<HttpClient> { HttpClientFactory.create(httpClientEngine(), get()) }
}
