package com.martdev.flickq.shared.domain.exception

data class ConflictException(val error: String = "conflict") : Exception(error)
