package com.martdev.flickq.shared.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import org.koin.dsl.bind
import org.koin.dsl.module

val httpClientModule = module {
    single {
        CIO.create()
    } bind HttpClientEngine::class
}