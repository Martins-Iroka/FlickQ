package com.martdev.flickq.features.payment.observability

interface PaymentEvents {
    fun paymentSucceeded(reservationId: Long, amount: Long)
    fun paymentFailed(reservationId: Long, reason: String, gatewayErrorCode: String?)
    fun refundProcessed(paymentId: Long, amount: Long)

    /**
     * A refund the server started on its own. [reason]: reservation_cancelled (paid after the hold ended) or
     * duplicate_charge; [outcome]: refund_pending or refund_failed (needs the admin refund retry).
     */
    fun automaticRefund(reservationId: Long, amount: Long, reason: String, outcome: String)

    /** Refunds sitting in REFUND_PENDING for days that Paystack doesn't show reversed; reported every reconcile run. */
    fun refundsStuck(count: Int)
//    fun fraudAlertTriggered(userId: Long, activityDetails: String)
}