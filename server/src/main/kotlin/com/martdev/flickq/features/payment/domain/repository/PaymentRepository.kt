package com.martdev.flickq.features.payment.domain.repository

import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.shared.domain.model.DataResult
import kotlin.time.Instant

interface PaymentRepository {
    suspend fun createPayment(payment: Payment): DataResult<Payment>

    suspend fun getPaymentByReference(reference: String): DataResult<Payment>

    suspend fun getPaymentsByReservationId(reservationId: Long): DataResult<List<Payment>>

    suspend fun getPaymentByReservationId(reservationId: Long): DataResult<Payment>

    suspend fun applyChargeResult(
        reference: String,
        status: PaymentStatus,
        gatewayResponse: String?,
        paystackTransactionId: String?,
        paidAt: Instant?,
        amountFromGateway: Long,
    ): DataResult<PaymentUpdate>

    suspend fun markRefundPending(reference: String): DataResult<PaymentUpdate>

    /** Tells the payer an automatic refund is under way. Idempotent per payment (outbox key). */
    suspend fun enqueueRefundNotice(
        reference: String,
        reason: NotificationEvent.PaymentRefunded.Reason
    ): DataResult<Unit>

    suspend fun markRefunded(reference: String, refundedAt: Instant): DataResult<PaymentUpdate>

    suspend fun markRefundFailed(reference: String, gatewayResponse: String?): DataResult<PaymentUpdate>

    suspend fun findPendingPaymentsOlderThan(threshold: Instant, giveUpBefore: Instant): DataResult<List<Payment>>

    /**
     * SUCCESS payments that still need settling: the reservation never reached CONFIRMED, or an earlier
     * SUCCESS payment already paid for it (a duplicate charge).
     */
    suspend fun findUnsettledSuccessfulPayments(): DataResult<List<Payment>>

    suspend fun findRefundPendingOlderThan(threshold: Instant): DataResult<List<Payment>>
}
