package com.martdev.flickq.shared.domain.exception

data class BadRequestException(val error: String = "bad request") : Exception(error)