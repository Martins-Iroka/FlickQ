package com.martdev.flickq.features.payment.infrastructure.paystack

import com.martdev.flickq.features.payment.infrastructure.paystack.dto.InitializeResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.RefundResponse
import com.martdev.flickq.features.payment.infrastructure.paystack.dto.VerifyResponse

interface PaystackClient {
    suspend fun initializeTransaction(
        email: String,
        amount: Long
    ): InitializeResponse

    suspend fun verifyTransaction(reference: String): VerifyResponse

    suspend fun refundTransaction(reference: String, amount: Long?): RefundResponse
}
