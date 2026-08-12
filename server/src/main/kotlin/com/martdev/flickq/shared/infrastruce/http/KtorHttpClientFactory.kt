package com.martdev.flickq.shared.infrastruce.http

import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.util.getLoggerFactory
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class KtorHttpClientFactory {

    private val log = getLoggerFactory(KtorHttpClientFactory::class.java.simpleName)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val ktorLogger = object : Logger {
        override fun log(message: String) {
            log.info(message)
        }
    }

    fun create(
        engine: HttpClientEngine? = null,
        configure: HttpClientConfig<*>.() -> Unit = {},
    ): HttpClient {
        val baseConfig: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                logger = ktorLogger
                level = LogLevel.INFO
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) {
                        val body = runCatching { response.bodyAsText() }.getOrDefault("")
                        val msg = "HTTP ${response.status.value}: $body"
                        log.warn(msg)
                        if (response.status.value in 400..499) throw BadRequestException(msg)
                        else throw InternalServerException()
                    }
                }
            }
            configure()
        }
        return if (engine != null) HttpClient(engine, baseConfig) else HttpClient(CIO, baseConfig)
    }
}
