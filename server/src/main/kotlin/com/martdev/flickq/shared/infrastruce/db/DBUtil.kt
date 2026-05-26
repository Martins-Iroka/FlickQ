package com.martdev.flickq.shared.infrastruce.db

import com.martdev.flickq.shared.domain.model.DataResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

suspend fun <T> withSuspendTransaction(block: suspend JdbcTransaction.() -> DataResult<T>): DataResult<T> =
    withContext(Dispatchers.IO) {
        try {
            suspendTransaction {
                addLogger(StdOutSqlLogger)
                try {
                    block()
                } catch (e: ExposedSQLException) {
                    handleDbException(e)
                } catch (e: Exception) {
                    DataResult.Failure.UnknownError(e.stackTraceToString())
                }
            }
        } catch (_: ExposedSQLException) {
            // UniqueViolation shouldn't be the db error type.
            // an exception thrown was BatchUpdateException(23514)
            DataResult.Failure.UniqueViolation
        } catch (e: Exception) {
            DataResult.Failure.UnknownError(e.stackTraceToString())
        }
    }

fun handleDbException(e: ExposedSQLException): DataResult.Failure {
    return when (e.sqlState) {
        "23505" -> DataResult.Failure.UniqueViolation
        "23503" -> DataResult.Failure.ForeignKeyViolation
        "23P01" -> DataResult.Failure.Conflict(e.stackTraceToString())
        else -> DataResult.Failure.UnknownError(e.stackTraceToString())
    }
}