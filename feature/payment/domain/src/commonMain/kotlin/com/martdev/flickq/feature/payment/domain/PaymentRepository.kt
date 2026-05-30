package com.martdev.flickq.feature.payment.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.payment.model.Payment

interface PaymentRepository {
    /** Mirrors POST /payment/initialize ({reservation_id} -> authorization_url + reference). */
    suspend fun initializePayment(reservationId: Long): Result<Payment, DataError>

    /** Mirrors GET /payment/verify/{reference}. */
    suspend fun verifyPayment(reference: String): Result<Payment, DataError>
}
