package com.martdev.flickq.shared.domain.exception

data class UnauthorizedException(val error: String = "unauthorized") : Exception(error)
