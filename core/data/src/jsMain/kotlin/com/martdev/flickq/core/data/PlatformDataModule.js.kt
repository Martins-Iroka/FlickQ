package com.martdev.flickq.core.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun httpClientEngine(): HttpClientEngine {
    installApiCredentialsShim()
    return Js.create()
}

actual fun platformDataModule(): Module = module {
    single<TokenStorage> { WebTokenStorage() }
}

/**
 * Ktor's JS engine issues `fetch` with the default `same-origin` credentials mode and exposes no
 * config to change it, so the browser won't attach the httpOnly refresh cookie on cross-origin API
 * calls (Part E / Decision #2). We patch `fetch` once to force `credentials: 'include'`, but ONLY
 * for requests to [AppConfig.BASE_ORIGIN] — Coil shares this same `fetch`, so scoping it keeps
 * cross-origin poster/CDN loads on the default mode and avoids credentialed-CORS failures.
 *
 * The origin is handed to JS via a global because Kotlin/JS `js(...)` only accepts a constant
 * string (no Kotlin interpolation). The patch is idempotent (guarded by `__flickqFetchPatched`).
 */
private fun installApiCredentialsShim() {
    val global = js("globalThis")
    global.__flickqApiOrigin = AppConfig.BASE_ORIGIN
    js(
        """
        (function () {
            if (typeof globalThis === 'undefined' || !globalThis.fetch || globalThis.__flickqFetchPatched) return;
            var origin = globalThis.__flickqApiOrigin;
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
        })();
        """
    )
}
