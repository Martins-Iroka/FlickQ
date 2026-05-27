package com.martdev.flickq.features.payment.infrastructure.paystack

import com.martdev.flickq.config.PaystackConfig
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.InitializeRequest
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.InitializeResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.RefundRequest
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.RefundResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.VerifyResponse
import com.martdev.flickq.shared.infrastruce.http.KtorHttpClientFactory
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import org.koin.core.annotation.Single

@Single
class PaystackHttpClient(
    private val config: PaystackConfig,
    httpClientFactory: KtorHttpClientFactory,
    engine: HttpClientEngine? = null,
) : PaystackClient {

    private val client = httpClientFactory.create(engine) {
        defaultRequest {
            header(HttpHeaders.Authorization, "Bearer ${config.secretKey}")
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun initializeTransaction(
        email: String,
        amount: Long
    ): InitializeResponse {
        val response = client.post("${config.baseUrl}/transaction/initialize") {
            setBody(
                InitializeRequest(
                    email = email,
                    amount = amount
                )
            )
        }
        return response.body<InitializeResponse>()
    }

    override suspend fun verifyTransaction(reference: String): VerifyResponse {
        val response = client.get("${config.baseUrl}/transaction/verify/$reference")
        return response.body<VerifyResponse>()
    }

    override suspend fun refundTransaction(reference: String, amount: Long?): RefundResponse {
        val response = client.post("${config.baseUrl}/refund") {
            setBody(RefundRequest(transaction = reference, amount = amount))
        }
        return response.body<RefundResponse>()
    }
}
