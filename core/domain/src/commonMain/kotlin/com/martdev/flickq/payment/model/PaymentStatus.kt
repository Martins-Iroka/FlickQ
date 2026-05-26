package com.martdev.flickq.payment.model

enum class PaymentStatus {
    INITIATED,
    PENDING,
    SUCCESS,
    FAILED,
    ABANDONED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED,
}
