package com.martdev.flickq.feature.payment.data

import com.martdev.flickq.payment.InitializePaymentResponse
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
    updatedAt = updatedAt ?: Clock.System.now()
)

/**
 * `initialize` returns only the gateway hand-off fields (authorization_url + reference), not
 * a full payment row — so the domain [Payment] is minted PENDING with those fields set.
 */
internal fun InitializePaymentResponse.toPayment(): Payment = Payment(
    reservationId = reservationId,
    reference = reference,
    authorizationUrl = authorizationUrl,
    accessCode = accessCode,
    status = PaymentStatus.PENDING
)

internal fun String.toPaymentStatus(): PaymentStatus =
    runCatching { PaymentStatus.valueOf(uppercase()) }.getOrDefault(PaymentStatus.PENDING)
