package com.martdev.flickq.shared.domain.exception

data class InternalServerException(val error: String = "The server encountered a problem") : Exception(error)
