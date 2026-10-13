package com.martdev.flickq.shared.infrastruce.db

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertFailsWith

@Testcontainers
class PgAdvisoryJobLockTest {

    companion object {
        private lateinit var db: Database
        private const val KEY = 0x464C4B51_00000063L

        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connect() {
            // URL-based connect → every connector() call is a separate session, like two Fly machines.
            db = Database.connect(postgres.jdbcUrl, "org.postgresql.Driver", postgres.username, postgres.password)
        }
    }

    @Test
    fun `only one holder runs, and the lock is free again afterwards`() = runBlocking {
        val inside = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async(Dispatchers.IO) {
            runExclusive(db, KEY) {
                inside.complete(Unit)
                release.await()
                "first"
            }
        }
        inside.await()

        assertThat(runExclusive(db, KEY) { "second" }).isNull()        // other "machine" skips

        release.complete(Unit)
        assertThat(first.await()).isEqualTo("first")
        assertThat(runExclusive(db, KEY) { "third" }).isEqualTo("third")
    }

    @Test
    fun `the lock is released when the job throws`() = runBlocking {
        assertFailsWith<IllegalStateException> { runExclusive(db, KEY) { error("job failed") } }

        assertThat(runExclusive(db, KEY) { "next" }).isEqualTo("next")
    }

    @Test
    fun `different keys don't block each other`() = runBlocking {
        val inside = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async(Dispatchers.IO) {
            runExclusive(db, KEY) {
                inside.complete(Unit)
                release.await()
            }
        }
        inside.await()

        assertThat(runExclusive(db, KEY + 1) { "other" }).isEqualTo("other")

        release.complete(Unit)
        holder.await()
    }
}