package com.martdev.flickq.features.payment.infrastructure.db.repository

import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.payment.domain.repository.PaymentRepository
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
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Instant

@Single
class PaymentRepositoryImpl : PaymentRepository {

    override suspend fun createPayment(payment: Payment): DataResult<Payment> = withSuspendTransaction {
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

    override suspend fun getPaymentByReference(reference: String): DataResult<Payment> = withSuspendTransaction {
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

    override suspend fun getPaymentsByUserId(userId: Long): DataResult<List<Payment>> = withSuspendTransaction {
        val payments = PaymentEntity.find { PaymentTable.userId eq userId }
            .orderBy(PaymentTable.createdAt to SortOrder.DESC)
            .map { it.toPayment() }
        DataResult.Success(payments)
    }

    override suspend fun applyChargeResult(
        reference: String,
        status: PaymentStatus,
        gatewayResponse: String?,
        paystackTransactionId: String?,
        paidAt: Instant?,
        amountFromGateway: Long,
    ): DataResult<Payment> = withSuspendTransaction {
        // Lock the payment row to serialize concurrent verify + webhook
        val locked = PaymentTable.selectAll()
            .where { PaymentTable.reference eq reference }
            .forUpdate()
            .firstOrNull()
            ?: return@withSuspendTransaction DataResult.Failure.NotFound("Payment not found.")

        val id = locked[PaymentTable.id].value
        val entity = PaymentEntity[id]

        // Idempotency: terminal states are never re-written
        if (entity.status == PaymentStatus.SUCCESS || entity.status == PaymentStatus.FAILED) {
            return@withSuspendTransaction DataResult.Success(entity.toPayment())
        }

        // Fraud guard: amount returned by Paystack must equal what we stored at initialize-time
        val resolvedStatus = if (status == PaymentStatus.SUCCESS && amountFromGateway != entity.amount) {
            PaymentStatus.FAILED
        } else {
            status
        }
        val resolvedGatewayResponse =
            if (resolvedStatus == PaymentStatus.FAILED && amountFromGateway != entity.amount) {
                "Amount mismatch: gateway=$amountFromGateway, expected=${entity.amount}"
            } else {
                gatewayResponse
            }

        entity.status = resolvedStatus
        entity.gatewayResponse = resolvedGatewayResponse
        entity.paystackTransactionId = paystackTransactionId ?: entity.paystackTransactionId
        if (resolvedStatus == PaymentStatus.SUCCESS) {
            entity.paidAt = paidAt ?: Clock.System.now()
        }
        entity.updatedAt = Clock.System.now()

        DataResult.Success(entity.toPayment())
    }

    override suspend fun markRefundPending(reference: String): DataResult<Payment> = withSuspendTransaction {
        val entity = PaymentEntity.find { PaymentTable.reference eq reference }
            .firstOrNull()
            ?: return@withSuspendTransaction DataResult.Failure.NotFound("Payment not found.")
        entity.status = PaymentStatus.REFUND_PENDING
        entity.updatedAt = Clock.System.now()
        DataResult.Success(entity.toPayment())
    }

    override suspend fun markRefunded(reference: String, refundedAt: Instant): DataResult<Payment> =
        withSuspendTransaction {
            val entity = PaymentEntity.find { PaymentTable.reference eq reference }
                .firstOrNull()
                ?: return@withSuspendTransaction DataResult.Failure.NotFound("Payment not found.")
            entity.status = PaymentStatus.REFUNDED
            entity.refundedAt = refundedAt
            entity.updatedAt = Clock.System.now()
            DataResult.Success(entity.toPayment())
        }

    override suspend fun markRefundFailed(reference: String, gatewayResponse: String?): DataResult<Payment> =
        withSuspendTransaction {
            val entity = PaymentEntity.find { PaymentTable.reference eq reference }
                .firstOrNull()
                ?: return@withSuspendTransaction DataResult.Failure.NotFound("Payment not found.")
            entity.status = PaymentStatus.REFUND_FAILED
            entity.gatewayResponse = gatewayResponse ?: entity.gatewayResponse
            entity.updatedAt = Clock.System.now()
            DataResult.Success(entity.toPayment())
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
}
