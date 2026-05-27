package com.martdev.flickq.features.payment.domain.service

import com.martdev.flickq.payment.model.Payment

interface PaymentService {
    suspend fun initializePayment(reservationId: Long, userId: Long): InitializePaymentResult

    /**
     * Verifies a payment with Paystack and applies the outcome (idempotently).
     * Callers from the JWT-authenticated endpoint pass [requestingUserId] for ownership check;
     * the public Paystack callback passes null.
     */
    suspend fun verifyPayment(reference: String, requestingUserId: Long?): Payment

    suspend fun handleWebhook(rawBody: String, signature: String?)

    suspend fun refundPaymentForReservation(reservationId: Long): Payment?

    suspend fun getMyPayments(userId: Long): List<Payment>

    suspend fun getPaymentsByReservationId(reservationId: Long): List<Payment>

    suspend fun reconcilePendingPayments()
}

data class InitializePaymentResult(
    val authorizationUrl: String,
    val accessCode: String,
    val reference: String,
    val reservationId: Long,
)
