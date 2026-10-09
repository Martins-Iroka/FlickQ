package com.martdev.flickq.features.payment.domain.repository

import com.martdev.flickq.payment.model.Payment

data class ChargeOutcome(
    val payment: Payment,
    val transitioned: Boolean
)
