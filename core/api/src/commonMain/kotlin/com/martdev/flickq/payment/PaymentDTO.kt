package com.martdev.flickq.payment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class InitializePaymentRequest(
    @SerialName("reservation_id") val reservationId: Long,
)

@Serializable
data class InitializePaymentResponse(
    @SerialName("authorization_url") val authorizationUrl: String,
    @SerialName("access_code") val accessCode: String,
    val reference: String,
    @SerialName("reservation_id") val reservationId: Long,
)

@Serializable
data class PaymentDTO(
    val id: Long = 0,
    @SerialName("reservation_id") val reservationId: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    val reference: String = "",
    val amount: Long = 0,
    val currency: String = "NGN",
    val status: String = "",
    @SerialName("authorization_url") val authorizationUrl: String? = null,
    @SerialName("paid_at") val paidAt: Instant? = null,
    @SerialName("refunded_at") val refundedAt: Instant? = null,
    @SerialName("gateway_response") val gatewayResponse: String? = null,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("updated_at") val updatedAt: Instant? = null,
)

@Serializable
data class PaymentCallbackResponse(
    val status: String,
    val reference: String,
    @SerialName("reservation_id") val reservationId: Long,
)
