package com.martdev.flickq.shared.util

import com.martdev.flickq.shared.domain.exception.BadRequestException
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.model.DataResult

fun <T> DataResult<T>.returnValue() = when (this) {
    is DataResult.Failure.NotFound -> throw NotFoundException(errorMessage)
    DataResult.Failure.UniqueViolation, DataResult.Failure.ForeignKeyViolation -> throw BadRequestException()
    is DataResult.Failure.UnknownError -> throw InternalServerException()
    is DataResult.Success -> value
    is DataResult.Failure.Conflict -> throw ConflictException(errorMessage)
}