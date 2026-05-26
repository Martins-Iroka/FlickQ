package com.martdev.flickq.shared.domain.exception

data class ForbiddenException(val error: String = "forbidden") : Exception(error)
