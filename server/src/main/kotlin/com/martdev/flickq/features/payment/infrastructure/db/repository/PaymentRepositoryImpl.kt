package com.martdev.flickq.features.payment.infrastructure.db.repository

import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.payment.domain.model.PaymentTransitions
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
import com.martdev.flickq.features.payment.domain.repository.PaymentUpdate
import com.martdev.flickq.features.payment.infrastructure.db.table.PaymentEntity
import com.martdev.flickq.features.payment.infrastructure.db.table.PaymentTable
import com.martdev.flickq.features.payment.infrastructure.db.table.toPayment
import com.martdev.flickq.features.reservation.infrastructure.db.table.ReservationTable
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.select
import org.koin.core.annotation.Single
import org.koin.core.annotation.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class PaymentRepositoryImpl : PaymentRepository {

    override suspend fun createPayment(payment: Payment): DataResult<Payment> =
        withSuspendTransaction {
            val entity = PaymentEntity.new {
                reservationId = EntityID(payment.reservationId, ReservationTable)
                userId = EntityID(payment.userId, UserTable)
                reference = payment.reference
                amount = payment.amount
                currency = payment.currency
                status = payment.status
                authorizationUrl = payment.authorizationUrl
                accessCode = payment.accessCode
                paystackTransactionId = payment.paystackTransactionId
                gatewayResponse = payment.gatewayResponse
                paidAt = payment.paidAt
                refundedAt = payment.refundedAt
            }
            DataResult.Success(entity.toPayment())
        }

    override suspend fun getPaymentByReference(reference: String): DataResult<Payment> =
        withSuspendTransaction {
            val entity = PaymentEntity.find { PaymentTable.reference eq reference }
                .firstOrNull()
                ?: return@withSuspendTransaction DataResult.Failure.NotFound("Payment not found.")
            DataResult.Success(entity.toPayment())
        }

    override suspend fun getPaymentsByReservationId(reservationId: Long): DataResult<List<Payment>> =
        withSuspendTransaction {
            val payments = PaymentEntity.find { PaymentTable.reservationId eq reservationId }
                .orderBy(PaymentTable.createdAt to SortOrder.DESC)
                .map { it.toPayment() }
            DataResult.Success(payments)
        }

    override suspend fun getPaymentByReservationId(reservationId: Long): DataResult<Payment> {
        return withSuspendTransaction {
            val result = PaymentTable.select(
                PaymentTable.authorizationUrl,
                PaymentTable.status,
                PaymentTable.reference
            ).where {
                PaymentTable.reservationId eq reservationId
            }.firstOrNull()
                ?: return@withSuspendTransaction DataResult.Failure.NotFound("Payment by reservation id not found")

            val authorizationUrl = result[PaymentTable.authorizationUrl]
            val reference = result[PaymentTable.reference]
            val status = result[PaymentTable.status]

            DataResult.Success(
                Payment(
                    authorizationUrl = authorizationUrl,
                    status = status,
                    reference = reference
                )
            )
        }
    }

    override suspend fun applyChargeResult(
        reference: String,
        status: PaymentStatus,
        gatewayResponse: String?,
        paystackTransactionId: String?,
        paidAt: Instant?,
        amountFromGateway: Long,
    ): DataResult<PaymentUpdate> = withSuspendTransaction {
        // Row lock serializes webhook + verify + reconciler for the same reference.
        val entity = lockByReference(reference)
            ?: return@withSuspendTransaction DataResult.Failure.NotFound("Payment not found.")
        val from = entity.status

        // Fraud guard: amount returned by Paystack must equal what we stored at initialize-time.
        val to =
            if (status == PaymentStatus.SUCCESS && amountFromGateway != entity.amount) PaymentStatus.FAILED else status

        val result = when {
            // Settled (SUCCESS, FAILED, any refund state): a charge result is history and never rewrites it.
            from !in PaymentTransitions.CHARGEABLE ->
                if (from == to) PaymentUpdate.Result.NO_OP else PaymentUpdate.Result.REJECTED

            from == to -> PaymentUpdate.Result.NO_OP
            !PaymentTransitions.canTransition(from, to) -> PaymentUpdate.Result.REJECTED
            else -> PaymentUpdate.Result.APPLIED
        }

        // Still unsettled: keep the latest gateway metadata even when the status itself didn't move
        // (preserves the existing PENDING → PENDING behaviour of recording the transaction id).
        if (from in PaymentTransitions.CHARGEABLE && result != PaymentUpdate.Result.REJECTED) {
            entity.status = to
            entity.gatewayResponse =
                if (to == PaymentStatus.FAILED && amountFromGateway != entity.amount) {
                    "Amount mismatch: gateway=$amountFromGateway, expected=${entity.amount}"
                } else {
                    gatewayResponse
                }
            entity.paystackTransactionId = paystackTransactionId ?: entity.paystackTransactionId
            if (to == PaymentStatus.SUCCESS) {
                entity.paidAt = paidAt ?: Clock.System.now()
            }
            entity.updatedAt = Clock.System.now()
        }

        DataResult.Success(
            PaymentUpdate(
                entity.toPayment(),
                result = result,
                previousStatus = from
            )
        )
    }

    override suspend fun markRefundPending(reference: String): DataResult<PaymentUpdate> =
        transition(reference, PaymentStatus.REFUND_PENDING)

    override suspend fun markRefunded(
        reference: String,
        refundedAt: Instant
    ): DataResult<PaymentUpdate> =
        transition(reference, PaymentStatus.REFUNDED) {
            this.refundedAt = refundedAt
        }

    override suspend fun markRefundFailed(
        reference: String,
        gatewayResponse: String?
    ): DataResult<PaymentUpdate> =
        transition(reference, PaymentStatus.REFUND_FAILED) {
            this.gatewayResponse = gatewayResponse ?: this.gatewayResponse
        }

    override suspend fun findPendingPaymentsOlderThan(
        threshold: Instant,
        giveUpBefore: Instant
    ): DataResult<List<Payment>> = withSuspendTransaction {
        val payments = PaymentEntity.find {
            (PaymentTable.status eq PaymentStatus.PENDING) and
                    (PaymentTable.createdAt less threshold) and
                    (PaymentTable.createdAt greater giveUpBefore)
        }.map { it.toPayment() }
        DataResult.Success(payments)
    }

    /**
     * Locks the row, then applies [to] only if the table allows it. A replay (already in [to]) and an
     * illegal move both write nothing, so a late or duplicated webhook can't overwrite a newer state.
     */
    private suspend fun transition(
        reference: String,
        to: PaymentStatus,
        write: PaymentEntity.() -> Unit = {},
    ): DataResult<PaymentUpdate> = withSuspendTransaction {
        val entity = lockByReference(reference)
            ?: return@withSuspendTransaction DataResult.Failure.NotFound("Payment not found.")
        val from = entity.status

        val result = when {
            from == to -> PaymentUpdate.Result.NO_OP
            !PaymentTransitions.canTransition(from, to) -> PaymentUpdate.Result.REJECTED
            else -> {
                entity.status = to
                entity.write()
                entity.updatedAt = Clock.System.now()
                PaymentUpdate.Result.APPLIED
            }
        }
        DataResult.Success(
            PaymentUpdate(
                entity.toPayment(),
                result = result,
                previousStatus = from
            )
        )
    }

    private fun lockByReference(reference: String): PaymentEntity? =
        PaymentTable.select(PaymentTable.id)
            .where { PaymentTable.reference eq reference }
            .forUpdate()
            .firstOrNull()
            ?.let { PaymentEntity[it[PaymentTable.id].value] }
}
