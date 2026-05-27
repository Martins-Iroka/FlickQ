package com.martdev.flickq.features.payment.infrastructure.paystack.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InitializeRequest(
    val email: String,
    val amount: Long
)

@Serializable
data class InitializeResponse(
    val status: Boolean = false,
    val message: String = "",
    val data: InitializeData? = null,
) {
    @Serializable
    data class InitializeData(
        @SerialName("authorization_url") val authorizationUrl: String = "",
        @SerialName("access_code") val accessCode: String = "",
        val reference: String = "",
    )
}

@Serializable
data class VerifyResponse(
    val status: Boolean = false,
    val message: String = "",
    val data: VerifyData? = null,
) {
    @Serializable
    data class VerifyData(
        val id: Long = 0,
        val status: String = "",
        val reference: String = "",
        val amount: Long = 0,
        val currency: String = "",
        @SerialName("paid_at") val paidAt: String? = null,
        @SerialName("gateway_response") val gatewayResponse: String? = null,
    )
}

@Serializable
data class RefundRequest(
    val transaction: String,
    val amount: Long? = null,
)

@Serializable
data class RefundResponse(
    val status: Boolean = false,
    val message: String = "",
    val data: RefundData? = null,
) {
    @Serializable
    data class RefundData(
        val id: Long = 0,
        val status: String = "",
        val transaction: RefundTransaction? = null,
    ) {
        @Serializable
        data class RefundTransaction(
            val id: Long = 0,
            val reference: String = "",
        )
    }
}

@Serializable
data class PaystackWebhookEvent(
    val event: String = "",
    val data: WebhookData = WebhookData(),
) {
    @Serializable
    data class WebhookData(
        val id: Long = 0,
        val reference: String = "",
        val status: String = "",
        val amount: Long = 0,
        val currency: String = "",
        @SerialName("paid_at") val paidAt: String? = null,
        @SerialName("gateway_response") val gatewayResponse: String? = null,
    )
}
