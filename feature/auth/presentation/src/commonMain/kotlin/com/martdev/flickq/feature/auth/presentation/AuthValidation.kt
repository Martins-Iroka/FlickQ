package com.martdev.flickq.feature.auth.presentation

internal fun isValidEmail(email: String): Boolean =
    email.isNotBlank() && email.contains("@") && email.substringAfter("@").contains(".")

internal fun isValidPassword(password: String): Boolean =
    password.length >= MIN_PASSWORD_LENGTH

internal const val MIN_PASSWORD_LENGTH = 6
internal const val OTP_LENGTH = 6
