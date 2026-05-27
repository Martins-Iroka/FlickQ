package com.martdev.features.payment.observability

interface PaymentEvents {
    fun paymentSucceeded(reservationId: Long, amount: Long)
    fun paymentFailed(reservationId: Long, reason: String, gatewayErrorCode: String?)
    fun refundProcessed(paymentId: Long, amount: Long)
//    fun fraudAlertTriggered(userId: Long, activityDetails: String)
}