package com.martdev.flickq.core.data

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun httpClientEngine(): HttpClientEngine = Darwin.create()

@OptIn(ExperimentalSettingsImplementation::class)
actual fun platformDataModule(): Module = module {
    single<TokenStorage> {
        val settings: Settings = KeychainSettings(service = "com.martdev.flickq.tokens")
        SettingsTokenStorage(settings)
    }
}
