package com.martdev.flickq.feature.payment.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.payment.model.Payment

interface PaymentRepository {
    suspend fun initializePayment(reservationId: Long): Result<Payment, DataError>

    suspend fun verifyPayment(reference: String): Result<Payment, DataError>

    suspend fun getInitializedPaymentData(reservationId: Long): Result<Payment, DataError>
}
