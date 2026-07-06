package com.martdev.flickq.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The subset of JWT payload claims the client cares about. Decoded WITHOUT verifying the
 * signature — purely to shape the UI (e.g. gate admin screens). The server's `withRole`
 * guard is the real authority; a forged client claim only changes what the UI offers, never
 * what the API permits.
 */
data class JwtClaims(
    val userId: String?,
    val role: String?,
    val exp: String?
) {
    val isAdmin: Boolean get() = role.equals("ADMIN", ignoreCase = true)
}

object JwtDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(token: String?): JwtClaims? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val payloadBytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(parts[1])
            val obj = json.parseToJsonElement(payloadBytes.decodeToString()).jsonObject
            JwtClaims(
                userId = obj["userId"]?.jsonPrimitive?.contentOrNull,
                role = obj["role"]?.jsonPrimitive?.contentOrNull,
                exp = obj["exp"]?.jsonPrimitive?.contentOrNull
            )
        } catch (e: Exception) {
            null
        }
    }
}
