package com.martdev.flickq.features.payment.domain.api

import com.martdev.flickq.features.payment.domain.service.InitializePaymentResult
import com.martdev.flickq.payment.InitializePaymentResponse
import com.martdev.flickq.payment.PaymentDTO
import com.martdev.flickq.payment.model.Payment

fun Payment.toPaymentDTO() = PaymentDTO(
    id = id,
    reservationId = reservationId,
    userId = userId,
    reference = reference,
    amount = amount,
    currency = currency,
    status = status.name,
    authorizationUrl = authorizationUrl,
    paidAt = paidAt,
    refundedAt = refundedAt,
    gatewayResponse = gatewayResponse,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun InitializePaymentResult.toInitializeResponse() = InitializePaymentResponse(
    authorizationUrl = authorizationUrl,
    accessCode = accessCode,
    reference = reference,
    reservationId = reservationId,
)
