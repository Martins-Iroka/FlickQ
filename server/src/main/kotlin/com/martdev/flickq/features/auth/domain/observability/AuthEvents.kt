package com.martdev.flickq.features.auth.domain.observability

interface AuthEvents {
    fun registerSucceeded(userId: Long)
    fun verifySucceeded(userId: Long)
    fun verifyFailed(reason: String)
    fun loginSucceeded(userId: Long)
    fun loginFailed(reason: String)
    fun refreshSucceeded(userId: Long)
    fun refreshFailed(reason: String)
    fun otpSendFailed()
    fun otpResendSucceeded(userId: Long)
}
