package com.martdev.flickq.features.payment.domain.repository

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

    suspend fun markRefunded(reference: String, refundedAt: Instant): DataResult<PaymentUpdate>

    suspend fun markRefundFailed(reference: String, gatewayResponse: String?): DataResult<PaymentUpdate>

    suspend fun findPendingPaymentsOlderThan(threshold: Instant, giveUpBefore: Instant): DataResult<List<Payment>>

    /** SUCCESS payments whose reservation never reached CONFIRMED: money taken, nothing delivered yet. */
    suspend fun findSuccessfulPaymentsWithUnconfirmedReservation(): DataResult<List<Payment>>
}
