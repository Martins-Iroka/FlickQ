package com.martdev.flickq.features.payment.domain.repository

import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus

data class PaymentUpdate(
    val payment: Payment,
    val result: Result,
    val previousStatus: PaymentStatus
) {
    enum class Result {
        APPLIED, // the status changed
        NO_OP,  // already in the requested status (replay)
        REJECTED, // not a legal transition: nothing written
    }
    val transitioned: Boolean get() = result == Result.APPLIED
}
