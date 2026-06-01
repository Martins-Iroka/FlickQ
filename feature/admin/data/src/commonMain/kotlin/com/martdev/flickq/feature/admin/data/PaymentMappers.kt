package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.payment.PaymentDTO
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import kotlin.time.Clock

internal fun PaymentDTO.toPayment(): Payment = Payment(
    id = id,
    reservationId = reservationId,
    userId = userId,
    reference = reference,
    amount = amount,
    currency = currency,
    status = status.toPaymentStatus(),
    authorizationUrl = authorizationUrl,
    gatewayResponse = gatewayResponse,
    paidAt = paidAt,
    refundedAt = refundedAt,
    createdAt = createdAt ?: Clock.System.now(),
    updatedAt = updatedAt ?: Clock.System.now(),
)

/** Free-string status; unknown values default to INITIATED rather than guessing a terminal state. */
internal fun String.toPaymentStatus(): PaymentStatus =
    runCatching { PaymentStatus.valueOf(uppercase()) }.getOrDefault(PaymentStatus.INITIATED)
