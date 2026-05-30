package com.martdev.flickq.core.presentation

import com.martdev.flickq.core.common.DataError

fun DataError.toUiText(): UiText = UiText.DynamicString(
    when (this) {
        DataError.Network.NO_INTERNET -> "No internet connection. Check your network and try again."
        DataError.Network.REQUEST_TIMEOUT -> "The request timed out. Please try again."
        DataError.Network.UNAUTHORIZED -> "Your session has expired. Please log in again."
        DataError.Network.FORBIDDEN -> "You don't have permission to do that."
        DataError.Network.NOT_FOUND -> "We couldn't find what you were looking for."
        DataError.Network.CONFLICT -> "That action conflicts with the current state. Please refresh."
        DataError.Network.BAD_REQUEST -> "Something about that request wasn't right."
        DataError.Network.TOO_MANY_REQUESTS -> "Too many attempts. Please wait a moment and try again."
        DataError.Network.PAYLOAD_TOO_LARGE -> "That request was too large."
        DataError.Network.SERVER_ERROR -> "Our servers hit a snag. Please try again shortly."
        DataError.Network.SERVICE_UNAVAILABLE -> "The service is temporarily unavailable."
        DataError.Network.SERIALIZATION -> "We received an unexpected response. Please try again."
        DataError.Network.UNKNOWN -> "Something went wrong. Please try again."
        DataError.Local.DISK_FULL -> "Your device is out of storage."
        DataError.Local.NOT_FOUND -> "We couldn't find that on your device."
        DataError.Local.UNKNOWN -> "Something went wrong. Please try again."
    }
)
