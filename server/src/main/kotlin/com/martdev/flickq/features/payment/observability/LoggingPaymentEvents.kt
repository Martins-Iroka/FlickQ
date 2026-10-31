package com.martdev.flickq.features.payment.observability

import com.martdev.flickq.shared.util.getLoggerFactory
import org.koin.core.annotation.Single
import org.koin.core.annotation.Singleton

@Singleton
class LoggingPaymentEvents : PaymentEvents {
    private val logger = getLoggerFactory(LoggingPaymentEvents::class.java.simpleName)

    override fun paymentSucceeded(reservationId: Long, amount: Long) {
        logger.info("event=payment.success reservation_id={}", reservationId)
    }

    override fun paymentFailed(
        reservationId: Long,
        reason: String,
        gatewayErrorCode: String?
    ) {
        logger.warn("event=payment.failure reservation_id={} reason={} gateway={}",
            reservationId, reason, gatewayErrorCode)
    }

    override fun refundProcessed(paymentId: Long, amount: Long) {
        logger.info("event=payment.refund payment_id={}", paymentId)
    }

    override fun automaticRefund(reservationId: Long, amount: Long, reason: String, outcome: String) {
        if (outcome == "refund_failed") {
            logger.error("event=payment.auto_refund reservation_id={} reason={} outcome={}", reservationId, reason, outcome)
        } else {
            logger.warn("event=payment.auto_refund reservation_id={} reason={} outcome={}", reservationId, reason, outcome)
        }
    }

    override fun refundsStuck(count: Int) {
        logger.error("event=payment.refunds_stuck count={}", count)
    }
}