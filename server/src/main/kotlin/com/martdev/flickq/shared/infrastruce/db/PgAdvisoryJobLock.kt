package com.martdev.flickq.shared.infrastruce.db

import com.martdev.flickq.shared.domain.job.JobLock
import com.martdev.flickq.shared.util.getLoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.annotation.Singleton
import java.sql.Connection

@Singleton
class PgAdvisoryJobLock : JobLock {
    override suspend fun <T> runExclusive(key: Long, block: suspend () -> T): T? =
        runExclusive(checkNotNull(TransactionManager.primaryDatabase) { "database is not connected" }, key, block)
}

private val log = getLoggerFactory("PgAdvisoryJobLock")

/**
 * Session-level `pg_try_advisory_lock` on a DEDICATED connection, held for the whole job.
 * - Not `pg_try_advisory_xact_lock`: the job runs its own transactions on other pooled connections,
 *   so the lock has to outlive all of them.
 * - Crash-safe: if this machine dies, Postgres ends the session and drops the lock, so the next tick
 *   on another machine takes over.
 * - Cost: one idle pooled connection per running job (minutes at most). The dispatcher's pool clamp
 *   (POOL_HEADROOM = 4) already covers the four jobs.
 */
internal suspend fun <T> runExclusive(db: Database, key: Long, block: suspend () -> T): T? =
    withContext(Dispatchers.IO) {
        val exposed = db.connector()
        try {
            val connection = exposed.connection as Connection
            if (!connection.queryBoolean("SELECT pg_try_advisory_lock(?)", key)) return@withContext null
            try {
                block()
            } finally {
                withContext(NonCancellable) { connection.unlock(key) }
            }
        } finally {
            exposed.close()          // back to Hikari
        }
    }

private fun Connection.unlock(key: Long) {
    val released = runCatching { queryBoolean("SELECT pg_advisory_unlock(?)", key) }
    if (released.getOrNull() == true) return
    // A pooled connection that still holds the lock would block this job on EVERY machine until Hikari
    // retires it (maxLifetime, 30 min). Abort it so the pool evicts it instead of reusing it.
    log.warn("advisory unlock {} failed; aborting the connection", key, released.exceptionOrNull())
    runCatching { abort(Runnable::run) }
}

private fun Connection.queryBoolean(sql: String, key: Long): Boolean =
    prepareStatement(sql).use { ps ->
        ps.setLong(1, key)
        ps.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
    }