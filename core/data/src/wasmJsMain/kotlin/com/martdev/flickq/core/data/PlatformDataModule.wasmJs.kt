package com.martdev.flickq.core.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun httpClientEngine(): HttpClientEngine = Js.create()

actual fun platformDataModule(): Module = module {
    single<TokenStorage> { WebTokenStorage() }
}
