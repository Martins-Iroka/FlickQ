package com.martdev.flickq.payment.model

import kotlin.time.Clock
import kotlin.time.Instant

data class Payment(
    val id: Long = 0,
    val reservationId: Long = 0,
    val userId: Long = 0,
    val reference: String = "",
    val amount: Long = 0,
    val currency: String = "NGN",
    val status: PaymentStatus = PaymentStatus.INITIATED,
    val authorizationUrl: String? = null,
    val accessCode: String? = null,
    val paystackTransactionId: String? = null,
    val gatewayResponse: String? = null,
    val paidAt: Instant? = null,
    val refundedAt: Instant? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
)
