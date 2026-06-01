package com.martdev.flickq.feature.admin.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.payment.model.Payment

/** Admin payment lookup over `admin/payment/by-reservation/{id}` (a reservation may have several attempts). */
interface AdminPaymentRepository {
    suspend fun getPaymentsByReservation(reservationId: Long): Result<List<Payment>, DataError>
}
