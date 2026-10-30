package com.martdev.flickq.features.payment.observability

interface PaymentEvents {
    fun paymentSucceeded(reservationId: Long, amount: Long)
    fun paymentFailed(reservationId: Long, reason: String, gatewayErrorCode: String?)
    fun refundProcessed(paymentId: Long, amount: Long)

    /** A charge succeeded for a reservation that was already cancelled; [outcome] is refund_pending or refund_failed. */
    fun strandedPaymentRefund(reservationId: Long, amount: Long, outcome: String)
//    fun fraudAlertTriggered(userId: Long, activityDetails: String)
}