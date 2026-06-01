package com.martdev.flickq.core.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun httpClientEngine(): HttpClientEngine = CIO.create()

actual fun platformDataModule(): Module = module {
    single<TokenStorage> { InMemoryTokenStorage() }
}
