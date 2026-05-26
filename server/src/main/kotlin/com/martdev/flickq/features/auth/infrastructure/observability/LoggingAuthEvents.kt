package com.martdev.flickq.features.auth.infrastructure.observability

import com.martdev.flickq.features.auth.domain.observability.AuthEvents
import com.martdev.flickq.features.auth.domain.observability.AuthMetrics
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

@Single
class LoggingAuthEvents(
    private val metrics: AuthMetrics
) : AuthEvents {
    private val logger = LoggerFactory.getLogger("auth.events")

    override fun registerSucceeded(userId: Long) {
        logger.info("event=register.success user_id={}", userId)
        metrics.count("auth.register.success")
    }

    override fun verifySucceeded(userId: Long) {
        logger.info("event=verify.success user_id={}", userId)
        metrics.count("auth.verify.success")
    }

    override fun verifyFailed(reason: String) {
        logger.warn("event=verify.failure reason={}", reason)
        metrics.count("auth.verify.failure", "reason" to reason)
    }

    override fun loginSucceeded(userId: Long) {
        logger.info("event=login.success user_id={}", userId)
        metrics.count("auth.login.success")
    }

    override fun loginFailed(reason: String) {
        logger.info("event=login.failure reason={}", reason)
        metrics.count("auth.login.failure", "reason" to reason)
    }

    override fun refreshSucceeded(userId: Long) {
        logger.info("event=refresh.success user_id={}", userId)
        metrics.count("auth.refresh.success")
    }

    override fun refreshFailed(reason: String) {
        logger.info("event=refresh.failure reason={}", reason)
        metrics.count("auth.refresh.failure", "reason" to reason)
    }

    override fun otpSendFailed() {
        logger.warn("event=otp.send.failure")
        metrics.count("auth.otp.send.failure")
    }

    override fun otpResendSucceeded(userId: Long) {
        logger.info("event=otp.resend.success user_id={}", userId)
        metrics.count("auth.otp.resend.success")
    }
}
