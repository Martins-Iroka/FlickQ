package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.admin.domain.AdminPaymentRepository
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import kotlin.time.Clock

/** Sample payments so the reservation detail screen renders a payment history on fakes. */
class FakeAdminPaymentDataSource : AdminPaymentRepository {

    override suspend fun getPaymentsByReservation(reservationId: Long): Result<List<Payment>, DataError> =
        Result.Success(
            listOf(
                Payment(
                    id = 1, reservationId = reservationId, userId = 11,
                    reference = "FQ-PAY-ABANDONED", amount = 7000, currency = "NGN",
                    status = PaymentStatus.ABANDONED, createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
                ),
                Payment(
                    id = 2, reservationId = reservationId, userId = 11,
                    reference = "FQ-PAY-SUCCESS", amount = 7000, currency = "NGN",
                    status = PaymentStatus.SUCCESS, paidAt = Clock.System.now(),
                    createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
                ),
            )
        )
}
