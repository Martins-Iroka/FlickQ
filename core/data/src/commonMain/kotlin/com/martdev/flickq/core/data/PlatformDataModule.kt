package com.martdev.flickq.core.data

import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module

/** The Ktor engine for the current platform (OkHttp / Darwin / Js / CIO). */
expect fun httpClientEngine(): HttpClientEngine

/**
 * Platform Koin module supplying the platform-specific [TokenStorage]:
 * - Android: EncryptedSharedPreferences (Keystore-backed)
 * - iOS: Keychain
 * - Web (js/wasm): [WebTokenStorage] (access token in memory; refresh in httpOnly cookie)
 * - JVM: in-memory (tests)
 *
 * Included alongside [coreDataModule] in `initKoin`.
 */
expect fun platformDataModule(): Module
