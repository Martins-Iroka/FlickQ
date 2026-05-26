package com.martdev.flickq.shared.infrastruce.http

import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

//@Single
class KtorHttpClientFactory {

    private val log = LoggerFactory.getLogger(KtorHttpClientFactory::class.java)

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
