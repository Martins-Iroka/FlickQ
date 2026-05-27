package com.martdev.flickq.features.payment.domain.repository

import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.shared.domain.model.DataResult
import kotlin.time.Instant

interface PaymentRepository {
    suspend fun createPayment(payment: Payment): DataResult<Payment>

    suspend fun getPaymentByReference(reference: String): DataResult<Payment>

    suspend fun getPaymentsByReservationId(reservationId: Long): DataResult<List<Payment>>

    suspend fun getPaymentsByUserId(userId: Long): DataResult<List<Payment>>

    suspend fun applyChargeResult(
        reference: String,
        status: PaymentStatus,
        gatewayResponse: String?,
        paystackTransactionId: String?,
        paidAt: Instant?,
        amountFromGateway: Long,
    ): DataResult<Payment>

    suspend fun markRefundPending(reference: String): DataResult<Payment>

    suspend fun markRefunded(reference: String, refundedAt: Instant): DataResult<Payment>

    suspend fun markRefundFailed(reference: String, gatewayResponse: String?): DataResult<Payment>

    suspend fun findPendingPaymentsOlderThan(threshold: Instant, giveUpBefore: Instant): DataResult<List<Payment>>
}
