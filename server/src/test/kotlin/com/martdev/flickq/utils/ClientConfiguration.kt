package com.martdev.flickq.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder

fun ApplicationTestBuilder.clientConfiguration(token: String = ""): HttpClient = createClient {
    install(ContentNegotiation) {
        json()
    }
    defaultRequest {
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        bearerAuth(token)
    }
}