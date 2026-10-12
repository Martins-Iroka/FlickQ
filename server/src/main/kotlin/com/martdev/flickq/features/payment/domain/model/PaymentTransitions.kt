package com.martdev.flickq.features.payment.domain.model

import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.payment.model.PaymentStatus.ABANDONED
import com.martdev.flickq.payment.model.PaymentStatus.FAILED
import com.martdev.flickq.payment.model.PaymentStatus.INITIATED
import com.martdev.flickq.payment.model.PaymentStatus.PENDING
import com.martdev.flickq.payment.model.PaymentStatus.REFUNDED
import com.martdev.flickq.payment.model.PaymentStatus.REFUND_FAILED
import com.martdev.flickq.payment.model.PaymentStatus.REFUND_PENDING
import com.martdev.flickq.payment.model.PaymentStatus.SUCCESS

/**
 * The only legal payment status changes. Gateway replays and out-of-order webhooks are refused here,
 * so a payment can never move backwards (e.g. REFUND_PENDING → SUCCESS).
 */
object PaymentTransitions {

    /** A gateway charge result still means something only before the charge has settled. */
    val CHARGEABLE: Set<PaymentStatus> = setOf(INITIATED, PENDING, ABANDONED)

    /** A refund may be (re)started from these. REFUND_FAILED is a retry. */
    val REFUNDABLE: Set<PaymentStatus> = setOf(SUCCESS, REFUND_FAILED)

    fun canTransition(from: PaymentStatus, to: PaymentStatus): Boolean = to in next(from)

    // Exhaustive `when`: a new PaymentStatus doesn't compile until its edges are decided.
    private fun next(from: PaymentStatus): Set<PaymentStatus> = when (from) {
        INITIATED -> setOf(PENDING, SUCCESS, FAILED, ABANDONED)
        PENDING -> setOf(SUCCESS, FAILED, ABANDONED)
        ABANDONED -> setOf(PENDING, SUCCESS, FAILED)          // Paystack can still complete an abandoned checkout
        SUCCESS -> setOf(REFUND_PENDING, REFUNDED)            // REFUNDED directly = refund issued from the Paystack dashboard
        REFUND_PENDING -> setOf(REFUNDED, REFUND_FAILED)
        REFUND_FAILED -> setOf(REFUND_PENDING, REFUNDED)      // admin retry, or a late refund.processed wins
        FAILED -> emptySet()
        REFUNDED -> emptySet()
    }
}