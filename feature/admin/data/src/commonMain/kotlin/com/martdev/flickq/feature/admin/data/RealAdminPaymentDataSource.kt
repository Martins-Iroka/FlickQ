package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.common.map
import com.martdev.flickq.core.data.getData
import com.martdev.flickq.feature.admin.domain.AdminPaymentRepository
import com.martdev.flickq.payment.PaymentDTO
import com.martdev.flickq.payment.model.Payment
import io.ktor.client.HttpClient

/** Ktor-backed admin payment lookup over `admin/payment/by-reservation/{id}`. */
class RealAdminPaymentDataSource(
    private val httpClient: HttpClient
) : AdminPaymentRepository {

    override suspend fun getPaymentsByReservation(reservationId: Long): Result<List<Payment>, DataError> =
        httpClient.getData<List<PaymentDTO>>("/admin/payment/by-reservation/$reservationId")
            .map { list -> list.map { it.toPayment() } }
}
