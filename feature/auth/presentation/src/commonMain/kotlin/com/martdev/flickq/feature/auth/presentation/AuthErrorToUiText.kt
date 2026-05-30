package com.martdev.flickq.feature.auth.presentation

import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.feature.auth.domain.AuthError

fun AuthError.toUiText(): UiText = UiText.DynamicString(
    when (this) {
        AuthError.INVALID_CREDENTIALS -> "Incorrect email or password."
        AuthError.EMAIL_ALREADY_REGISTERED -> "That email is already registered. Try logging in."
        AuthError.INVALID_OTP -> "That code isn't right. Please check and try again."
        AuthError.UNKNOWN -> "Something went wrong. Please try again."
    }
)
