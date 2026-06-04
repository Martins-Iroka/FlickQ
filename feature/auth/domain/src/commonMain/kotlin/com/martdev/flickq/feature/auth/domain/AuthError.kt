package com.martdev.flickq.feature.auth.domain

import com.martdev.flickq.core.common.Error

enum class AuthError : Error {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_REGISTERED,
    INVALID_OTP,
    NO_INTERNET,
    EMAIL_NOT_VERIFIED,
    UNKNOWN
}
