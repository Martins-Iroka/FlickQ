package com.martdev.flickq.core.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun httpClientEngine(): HttpClientEngine {
    installApiCredentialsShim(AppConfig.BASE_ORIGIN)
    return Js.create()
}

actual fun platformDataModule(): Module = module {
    single<TokenStorage> { WebTokenStorage() }
}

/**
 * Ktor's WasmJs engine issues `fetch` with the default `same-origin` credentials mode and exposes
 * no config to change it, so the browser won't attach the httpOnly refresh cookie on cross-origin
 * API calls (Part E / Decision #2). We patch `fetch` once to force `credentials: 'include'`, but
 * ONLY for requests to [origin] ([AppConfig.BASE_ORIGIN]) — Coil shares this same `fetch`, so
 * scoping it keeps cross-origin poster/CDN loads on the default mode and avoids credentialed-CORS
 * failures. Idempotent (guarded by `__flickqFetchPatched`). Kotlin/Wasm `js(...)` can read the
 * function's [origin] parameter directly.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun installApiCredentialsShim(origin: String): Unit = js(
    // Wrapped in an IIFE so the whole thing is ONE expression: Kotlin/Wasm emits this js() body as
    // an arrow function inside an object literal (`'name': (origin) => BODY,`), and a bare list of
    // statements (with an early `return`) is not a valid arrow body. The IIFE makes it valid and
    // also gives the early-`return` guard a function to return from. No trailing `;` — that would
    // break the surrounding object literal. `origin` is the arrow param, captured by the IIFE.
    """
    (function () {
        if (typeof globalThis === 'undefined' || !globalThis.fetch || globalThis.__flickqFetchPatched) return;
        var original = globalThis.fetch.bind(globalThis);
        globalThis.fetch = function (input, init) {
            init = init || {};
            if (init.credentials === undefined) {
                var url = (typeof input === 'string') ? input : (input && input.url) || '';
                if (origin && url.indexOf(origin) === 0) {
                    init.credentials = 'include';
                }
            }
            return original(input, init);
        };
        globalThis.__flickqFetchPatched = true;
    })()
    """
)
