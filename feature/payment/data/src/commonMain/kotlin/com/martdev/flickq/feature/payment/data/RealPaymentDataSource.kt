package com.martdev.flickq.feature.payment.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.core.data.postData
import com.martdev.flickq.feature.payment.domain.PaymentRepository
import com.martdev.flickq.payment.InitializePaymentRequest
import com.martdev.flickq.payment.InitializePaymentResponse
import com.martdev.flickq.payment.PaymentDTO
import com.martdev.flickq.payment.model.Payment
import io.ktor.client.HttpClient

/**
 * Ktor-backed [PaymentRepository]. `initialize` returns the Paystack hand-off (authorization
 * url + reference); the caller opens that url and then polls `verify/{reference}`. Used when
 * [com.martdev.flickq.core.data.AppConfig.USE_FAKES] is false.
 */
class RealPaymentDataSource(
    private val client: HttpClient
) : PaymentRepository {

    override suspend fun initializePayment(reservationId: Long): Result<Payment, DataError> =
        client.postData<InitializePaymentRequest, InitializePaymentResponse>(
            "/payment/initialize",
            InitializePaymentRequest(reservationId = reservationId)
        ).map { it.toPayment() }

    override suspend fun verifyPayment(reference: String): Result<Payment, DataError> =
        client.getData<PaymentDTO>("/payment/verify/$reference")
            .map { it.toPayment() }
}
