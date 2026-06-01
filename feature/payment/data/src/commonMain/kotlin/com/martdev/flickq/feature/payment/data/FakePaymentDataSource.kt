package com.martdev.flickq.feature.payment.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.payment.domain.PaymentRepository
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import kotlin.time.Clock

/**
 * In-memory payments used while the app runs on fakes. initializePayment mints a
 * PENDING payment (with a fake Paystack authorization_url + reference) and
 * verifyPayment flips it to SUCCESS, mirroring the real
 * initialize -> authorization_url -> verify round-trip. Swapped for a
 * Ktor-backed implementation (POST /payment/initialize, GET /payment/verify/{reference})
 * when wiring the real API; the real flow also opens authorization_url via a
 * platform UrlOpener before verifying.
 */
class FakePaymentDataSource : PaymentRepository {

    private val seatPrice = 3500L

    // reference -> payment
    private val payments = mutableMapOf<String, Payment>()
    private var paymentSeq = 1L

    override suspend fun initializePayment(reservationId: Long): Result<Payment, DataError> {
        if (reservationId <= 0) return Result.Error(DataError.Network.BAD_REQUEST)
        val id = paymentSeq++
        val reference = "FQ-PAY-" + id.toString().padStart(6, '0')
        // No reservation context on the fake; derive a plausible 1-4 seat amount.
        val amount = ((reservationId % 4) + 1) * seatPrice
        val payment = Payment(
            id = id,
            reservationId = reservationId,
            reference = reference,
            amount = amount,
            status = PaymentStatus.PENDING,
            // No real gateway on the fake: a null authorization url tells the ViewModel to
            // skip the browser hand-off and go straight to (immediately succeeding) verify.
            authorizationUrl = null,
            accessCode = "acc_$reference",
        )
        payments[reference] = payment
        return Result.Success(payment)
    }

    override suspend fun verifyPayment(reference: String): Result<Payment, DataError> {
        val existing = payments[reference] ?: return Result.Error(DataError.Network.NOT_FOUND)
        val paid = existing.copy(
            status = PaymentStatus.SUCCESS,
            paidAt = Clock.System.now(),
            gatewayResponse = "Approved",
        )
        payments[reference] = paid
        return Result.Success(paid)
    }
}
