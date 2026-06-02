package com.martdev.flickq.core.data

import com.martdev.flickq.auth.request.RefreshTokenRequest
import com.martdev.flickq.auth.response.RefreshTokenResponse
import com.martdev.flickq.shared.DataResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the shared Ktor [HttpClient]. The engine is injected so each platform supplies
 * its own (and tests can pass a mock engine). The Bearer [Auth] plugin attaches the access
 * token and transparently refreshes it on a 401 via `/authentication/refresh-token`.
 */
object HttpClientFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // DTOs use kotlin.time.Instant, which carries its own serializer — no module needed.
    }

    fun create(engine: HttpClientEngine, tokenStorage: TokenStorage): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            install(Logging) {
                level = LogLevel.INFO
                // Defence in depth: never let the access/refresh token reach logs.
                sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        tokenStorage.getAccessToken()?.let { access ->
                            BearerTokens(access, tokenStorage.getRefreshToken() ?: "")
                        }
                    }
                    refreshTokens {
                        val refresh = tokenStorage.getRefreshToken()
                        val response = client.post("${AppConfig.BASE_URL}/authentication/refresh-token") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            // Native sends the stored refresh token; on web it is null and the
                            // server reads it from the httpOnly cookie instead (Part E). The web
                            // engines force `credentials: 'include'` for API-origin calls (see the
                            // js/wasmJs PlatformDataModule shim) so that cookie rides along.
                            if (refresh != null) setBody(RefreshTokenRequest(refresh))
                        }
                        if (response.status.isSuccess()) {
                            val tokens = response.body<DataResponse<RefreshTokenResponse>>().data
                            tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
                            BearerTokens(tokens.accessToken, tokens.refreshToken)
                        } else {
                            // Refresh failed (expired/revoked) — drop tokens so the app logs out.
                            tokenStorage.clear()
                            null
                        }
                    }
                }
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }
}
