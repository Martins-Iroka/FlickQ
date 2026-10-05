package com.martdev.flickq.features.notification.domain.service

enum class FcmErrorClass {
    TRANSIENT, INVALID_TOKEN, CONFIG, FATAL
}

object FcmErrorClassifier {
    private val INVALID_TOKEN_CODES = setOf(
        "UNREGISTERED", "SENDER_ID_MISMATCH", "TOO_MANY_TOPICS"
    )
    private val TRANSIENT_CODES = setOf("QUOTA_EXCEEDED", "UNAVAILABLE", "INTERNAL", "IO")
    private val FATAL_CODES = setOf("UNAUTHENTICATED", "PERMISSION_DENIED")

    fun classify(error: FcmError, targetsToken: Boolean): FcmErrorClass = when {
        error.code in INVALID_TOKEN_CODES -> FcmErrorClass.INVALID_TOKEN
        error.code == "INVALID_ARGUMENT" -> if (targetsToken) FcmErrorClass.INVALID_TOKEN else FcmErrorClass.FATAL
        error.code == "THIRD_PARTY_AUTH_ERROR" -> FcmErrorClass.CONFIG
        error.code in TRANSIENT_CODES -> FcmErrorClass.TRANSIENT
        error.code in FATAL_CODES || error.httpStatus == 401 || error.httpStatus == 403 -> FcmErrorClass.FATAL
        error.httpStatus == 429 || (error.httpStatus ?: 0) >= 500 -> FcmErrorClass.TRANSIENT
        else -> FcmErrorClass.TRANSIENT
    }
}