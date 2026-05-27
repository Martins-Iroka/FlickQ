package com.martdev.flickq.features.payment.infrastructure.db.table

import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.reservation.infrastructure.db.table.ReservationTable
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.shared.infrastruce.db.setEnumeration
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object PaymentTable : LongIdTable("payments") {
    val reservationId = reference("reservation_id", ReservationTable)
    val userId = reference("user_id", UserTable)
    val reference = varchar("reference", 100).uniqueIndex()
    val amount = long("amount")
    val currency = varchar("currency", 8).default("NGN")
    val status = setEnumeration<PaymentStatus>("status", "payment_status")
    val authorizationUrl = text("authorization_url").nullable()
    val accessCode = text("access_code").nullable()
    val paystackTransactionId = varchar("paystack_transaction_id", 100).nullable()
    val gatewayResponse = text("gateway_response").nullable()
    val paidAt = timestamp("paid_at").nullable()
    val refundedAt = timestamp("refunded_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

class PaymentEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PaymentEntity>(PaymentTable)

    var reservationId by PaymentTable.reservationId
    var userId by PaymentTable.userId
    var reference by PaymentTable.reference
    var amount by PaymentTable.amount
    var currency by PaymentTable.currency
    var status by PaymentTable.status
    var authorizationUrl by PaymentTable.authorizationUrl
    var accessCode by PaymentTable.accessCode
    var paystackTransactionId by PaymentTable.paystackTransactionId
    var gatewayResponse by PaymentTable.gatewayResponse
    var paidAt by PaymentTable.paidAt
    var refundedAt by PaymentTable.refundedAt
    var createdAt by PaymentTable.createdAt
    var updatedAt by PaymentTable.updatedAt
}

fun PaymentEntity.toPayment() = Payment(
    id = id.value,
    reservationId = reservationId.value,
    userId = userId.value,
    reference = reference,
    amount = amount,
    currency = currency,
    status = status,
    authorizationUrl = authorizationUrl,
    accessCode = accessCode,
    paystackTransactionId = paystackTransactionId,
    gatewayResponse = gatewayResponse,
    paidAt = paidAt,
    refundedAt = refundedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
