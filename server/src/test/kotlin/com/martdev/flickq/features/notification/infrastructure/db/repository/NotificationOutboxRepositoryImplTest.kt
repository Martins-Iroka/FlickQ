package com.martdev.flickq.features.notification.infrastructure.db.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.features.notification.domain.service.NotificationComposer
import com.martdev.flickq.features.notification.infrastructure.db.table.NotificationOutboxTable
import com.martdev.flickq.features.notification.infrastructure.db.table.StoredPush
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Testcontainers
class NotificationOutboxRepositoryImplTest {

    companion object {
        private lateinit var repository: NotificationOutboxRepository

        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            val config = FcmConfig(false, "p", "", false, "dev-")
            val composer = NotificationComposer(config)
            val outboxWriter = NotificationOutboxWriter(composer)
            repository = NotificationOutboxRepositoryImpl(
                outboxWriter
            )
        }
    }

    @BeforeEach
    fun clean() {
        transaction {
            NotificationOutboxTable.deleteAll()
        }
    }

    @Test
    fun `claim returns only due pending rows and flips them in IN_FLIGHT with a lease`() = runTest {
        val due = insertRow()
        insertRow(dueIn = 1.hours)
        insertRow(status = NotificationStatus.SENT)

        val claimed = claim("w1")

        assertThat(claimed.map { it.id }).containsExactly(due)

        val r = row(due)

        assertThat(r[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.IN_FLIGHT)
        assertThat(r[NotificationOutboxTable.attempts]).isEqualTo(1)
        assertThat(r[NotificationOutboxTable.lockedBy]).isEqualTo("w1")
        assertThat(r[NotificationOutboxTable.lockedUntil]!!).isGreaterThan(Clock.System.now())
    }

    @Test
    fun `claim honours the limit and takes the oldest due rows first`() = runTest {
        val oldest = insertRow(dueIn = (-10).minutes)
        val middle = insertRow(dueIn = (-5).minutes)
        insertRow(dueIn = (-1).minutes)

        val claimed = claim("w1", limit = 2)

        assertThat(claimed.map { it.id }).containsExactly(oldest, middle)
    }

    @Test
    fun `a concurrent claimer skips rows another worker has locked and gets disjoint rows`() =
        runTest {
            repeat(20) {
                insertRow()
            }
            val holding = CountDownLatch(1)
            val release = CountDownLatch(1)
            val heldIds = mutableListOf<Long>()

            // Worker A: locks 10 rows with the SAME clause the claim uses, and holds them uncommitted
            val holder = async(Dispatchers.IO) {
                transaction {
                    exec(
                        "SELECT id FROM notification_outbox WHERE status = 'PENDING' " +
                                "ORDER BY next_attempt_at LIMIT 10 FOR UPDATE SKIP LOCKED"
                    ) { rs ->
                        while (rs.next()) heldIds += rs.getLong(1)
                    }
                    holding.countDown()
                    release.await()
                }
            }

            holding.await()

            val claimed =
                claim("w2", limit = 20) // B must not block on A, and must not get A's rows

            release.countDown()
            holder.await()

            assertThat(heldIds.size).isEqualTo(10)
            assertThat(claimed.size).isEqualTo(10)
            assertThat(claimed.map { it.id }.intersect(heldIds.toSet())).isEmpty()
        }

    @Test
    fun `an expired lease is reclaimed by another worker and attempts goes up`() = runTest {
        val id = insertRow()
        claim("w1")

        transaction {
            NotificationOutboxTable.update({
                NotificationOutboxTable.id eq id
            }) {
                it[lockedUntil] = Clock.System.now() - 1.minutes
            }
        }

        val reclaimed = claim("w2")
        assertThat(reclaimed.map { it.id }).containsExactly(id)
        val row = row(id)
        val lockedBy = row[NotificationOutboxTable.lockedBy]
        assertThat(lockedBy).isEqualTo("w2")
        val attempts = row[NotificationOutboxTable.attempts]
        assertThat(attempts).isEqualTo(2)
    }

    @Test
    fun `a live lease is not reclaimed`() = runTest {
        insertRow()
        claim("w1")
        val reclaimed = claim("w2")

        assertThat(reclaimed).isEmpty()
    }

    @Test
    fun `markSent by the lease holder clears the lease and records the note`() = runTest {
        val id = insertRow()
        claim("w1")

        val applied = repository.markSent(id, "w1", "no_devices")

        assertTrue(applied is DataResult.Success)
        assertThat(applied.value).isTrue()

        val r = row(id)
        assertThat(r[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.SENT)
        assertThat(r[NotificationOutboxTable.sentAt]).isNotNull()
        assertThat(r[NotificationOutboxTable.lockedBy]).isNull()
        assertThat(r[NotificationOutboxTable.lockedUntil]).isNull()
        assertThat(r[NotificationOutboxTable.lastError]).isEqualTo("no_devices")
    }

    @Test
    fun `a settle from a worker that lost the lease changes nothing`() = runTest {
        val id = insertRow()
        claim("w1")

        val applied = repository.markSent(id, "w2")

        assertTrue(applied is DataResult.Success)
        assertThat(applied.value).isFalse()

        val row = row(id)
        assertThat(row[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.IN_FLIGHT)
        assertThat(row[NotificationOutboxTable.lockedBy]).isEqualTo("w1")
    }

    @Test
    fun `markRetry puts the row back to pending for later and is not claimable until then`() =
        runTest {
            val id = insertRow()
            claim("w1")
            val next = (Clock.System.now() + 10.minutes).let {
                Instant.fromEpochSeconds(it.epochSeconds)
            }

            repository.markRetry(id, "w1", next, "UNAVAILABLE (503)", null)

            val r = row(id)

            assertThat(r[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.PENDING)
            assertThat(r[NotificationOutboxTable.nextAttemptAt]).isEqualTo(next)
            assertThat(r[NotificationOutboxTable.lastError]).isEqualTo("UNAVAILABLE (503)")
            assertThat(r[NotificationOutboxTable.attempts]).isEqualTo(1)
            assertThat(r[NotificationOutboxTable.lockedBy]).isNull()

            val claimed = claim("w1")

            assertThat(claimed).isEmpty()
        }

    @Test
    fun `markDead and markExpired set their status and clear the lease`() = runTest {
        val dead = insertRow()
        val expired = insertRow()
        claim("w1")

        repository.markDead(dead, "w1", "bad payload")
        repository.markExpired(expired, "w1")

        val deadRow = row(dead)

        assertThat(deadRow[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.DEAD)
        assertThat(deadRow[NotificationOutboxTable.lastError]).isEqualTo("bad payload")

        val expiredRow = row(expired)

        assertThat(expiredRow[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.EXPIRED)
        assertThat(expiredRow[NotificationOutboxTable.lockedUntil]).isNull()
    }

    @Test
    fun `release by the lease holder returns the row to pending and refunds the attempt`() = runTest {
        val id = insertRow()
        claim("w1")

        val applied = repository.release(id, "w1")

        assertTrue(applied is DataResult.Success)
        assertThat(applied.value).isTrue()
        val r = row(id)
        assertThat(r[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.PENDING)
        assertThat(r[NotificationOutboxTable.attempts]).isEqualTo(0)
        assertThat(r[NotificationOutboxTable.lockedBy]).isNull()
        assertThat(r[NotificationOutboxTable.lockedUntil]).isNull()

        assertThat(claim("w2").map { it.id }).containsExactly(id)   // immediately claimable elsewhere
    }

    @Test
    fun `release from a worker that lost the lease changes nothing`() = runTest {
        val id = insertRow()
        claim("w1")

        val applied = repository.release(id, "w2")

        assertTrue(applied is DataResult.Success)
        assertThat(applied.value).isFalse()
        val r = row(id)
        assertThat(r[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.IN_FLIGHT)
        assertThat(r[NotificationOutboxTable.attempts]).isEqualTo(1)
    }

    private fun insertRow(
        key: String = Uuid.random().toString(),
        status: NotificationStatus = NotificationStatus.PENDING,
        dueIn: Duration = (-1).minutes
    ): Long = transaction {

        NotificationOutboxTable.insertAndGetId {
            it[type] = NotificationType.MOVIE_ADDED
            it[idempotencyKey] = key
            it[targetKind] = NotificationTargetKind.TOPIC
            it[topic] = "dev-new-movies"
            it[payload] = StoredPush(title = "t", body = "b")
            it[this.status] = status
            it[nextAttemptAt] = Clock.System.now() + dueIn
        }.value
    }

    private fun row(id: Long) = transaction {

        NotificationOutboxTable.selectAll().where {
            NotificationOutboxTable.id eq id
        }.single()
    }

    private suspend fun claim(by: String, limit: Int = 10) =
        (repository.claimBatch(by, limit, 60.seconds) as DataResult.Success).value
}