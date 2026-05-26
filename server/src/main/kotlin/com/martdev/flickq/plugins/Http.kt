package com.martdev.flickq.plugins

import com.martdev.flickq.config.CorsConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import org.koin.ktor.ext.inject

fun Application.configureHttp() {
    val corsConfig by inject<CorsConfig>()
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        if (corsConfig.allowAnyHost) {
            anyHost()
        } else {
            corsConfig.allowedHosts.forEach { host ->
                allowHost(host, schemes = listOf("https", "http"))
            }
        }
    }
    routing {
        swaggerUI(path = "/swaggerUI") {
            info = OpenApiInfo(
                title = "Movie Reservation System",
                version = "1.0.0",
                description = "An application to enable users book movie tickets and admins to add movie for viewing",
                termsOfService = "https://swagger.io/terms/"
            )
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }
    }
}
