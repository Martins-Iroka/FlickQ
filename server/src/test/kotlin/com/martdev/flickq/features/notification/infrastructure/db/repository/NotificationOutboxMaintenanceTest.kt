package com.martdev.flickq.features.notification.infrastructure.db.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import com.martdev.flickq.features.notification.domain.model.OutboxStats
import com.martdev.flickq.features.notification.domain.observability.NoopNotificationMetrics
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.features.notification.domain.service.NotificationComposer
import com.martdev.flickq.features.notification.infrastructure.db.table.NotificationOutboxTable
import com.martdev.flickq.features.notification.infrastructure.db.table.StoredPush
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.notification.model.NotificationStatus.DEAD
import com.martdev.flickq.notification.model.NotificationStatus.EXPIRED
import com.martdev.flickq.notification.model.NotificationStatus.IN_FLIGHT
import com.martdev.flickq.notification.model.NotificationStatus.PENDING
import com.martdev.flickq.notification.model.NotificationStatus.SENT
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.utils.PostgresContainer
import com.martdev.flickq.utils.TestNotifications
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Testcontainers
class NotificationOutboxMaintenanceTest {

    companion object {
        private lateinit var repository: NotificationOutboxRepository

        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connect() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            repository = NotificationOutboxRepositoryImpl(
                NotificationOutboxWriter(NotificationComposer(TestNotifications.config), NoopNotificationMetrics)
            )
        }
    }

    @BeforeEach
    fun clean() {
        transaction { NotificationOutboxTable.deleteAll() }
    }

    @Test
    fun `claimed entries carry the stored traceparent`() = runTest {
        val tp = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        insertRow(traceParent = tp)

        val claimed = (repository.claimBatch("w1", 10, 60.seconds) as DataResult.Success).value

        assertThat(claimed.single().traceParent).isEqualTo(tp)
    }

    @Test
    fun `stats report the oldest DUE pending row and counts by status`() = runTest {
        insertRow(dueIn = (-90).seconds)
        insertRow(dueIn = (-10).seconds)
        insertRow(dueIn = 1.hours)                                     // backing off: pending, but not late
        insertRow(status = IN_FLIGHT)
        insertRow(status = DEAD)
        insertRow(status = SENT)

        val stats = (repository.stats() as DataResult.Success).value

        assertThat(stats.lagSeconds).isBetween(89.0, 95.0)            // TIMESTAMP(0) + test wall time
        assertThat(stats.copy(lagSeconds = 0.0)).isEqualTo(OutboxStats(0.0, pending = 3, inFlight = 1, dead = 1))
    }

    @Test
    fun `nothing due means zero lag`() = runTest {
        insertRow(dueIn = 1.hours)

        assertThat((repository.stats() as DataResult.Success).value.lagSeconds).isEqualTo(0.0)
    }

    @Test
    fun `purge deletes only old rows in the given statuses, in chunks`() = runTest {
        repeat(3) { insertRow(status = SENT, age = 8.days) }
        insertRow(status = EXPIRED, age = 8.days)
        val recentSent = insertRow(status = SENT, age = 1.days)
        val oldDead = insertRow(status = DEAD, age = 8.days)
        val oldPending = insertRow(status = PENDING, age = 8.days)
        val cutoff = Clock.System.now() - 7.days

        assertThat(purge(setOf(SENT, EXPIRED), cutoff, 3)).isEqualTo(3)
        assertThat(purge(setOf(SENT, EXPIRED), cutoff, 3)).isEqualTo(1)
        assertThat(purge(setOf(SENT, EXPIRED), cutoff, 3)).isEqualTo(0)

        assertThat(remainingIds()).containsExactlyInAnyOrder(recentSent, oldDead, oldPending)
    }

    @Test
    fun `purge refuses non-terminal statuses`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            repository.purgeFinished(setOf(PENDING), Clock.System.now(), 10)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.purgeFinished(setOf(IN_FLIGHT, SENT), Clock.System.now(), 10)
        }
    }

    @Test
    fun `list filters by status newest first with paging`() = runTest {
        val d1 = insertRow(status = DEAD)
        insertRow(status = SENT)
        val d2 = insertRow(status = DEAD)
        val d3 = insertRow(status = DEAD)

        val page1 = (repository.list(DEAD, 2, 0) as DataResult.Success).value
        val page2 = (repository.list(DEAD, 2, 2) as DataResult.Success).value
        val all = (repository.list(null, 10, 0) as DataResult.Success).value

        assertThat(page1.map { it.id }).containsExactly(d3, d2)
        assertThat(page2.map { it.id }).containsExactly(d1)
        assertThat(all.size).isEqualTo(4)
        assertThat(page1.first().title).isEqualTo("t")
    }

    @Test
    fun `requeue gives a DEAD row a fresh budget and makes it claimable`() = runTest {
        val id = insertRow(status = DEAD, attempts = 8, dueIn = 1.hours)

        assertThat(repository.requeueDead(id)).isEqualTo(DataResult.Success(Unit))

        val r = transaction { NotificationOutboxTable.selectAll().where { NotificationOutboxTable.id eq id }.single() }
        assertThat(r[NotificationOutboxTable.status]).isEqualTo(PENDING)
        assertThat(r[NotificationOutboxTable.attempts]).isEqualTo(0)
        assertThat(r[NotificationOutboxTable.nextAttemptAt]).isLessThanOrEqualTo(Clock.System.now())
        val claimed = (repository.claimBatch("w1", 10, 60.seconds) as DataResult.Success).value
        assertThat(claimed.map { it.id }).containsExactly(id)
    }

    @Test
    fun `requeue of a non-DEAD row is a conflict and of a missing row is not found`() = runTest {
        val sent = insertRow(status = SENT)

        assertThat(repository.requeueDead(sent)).isInstanceOf(DataResult.Failure.Conflict::class)
        assertThat(repository.requeueDead(999_999)).isInstanceOf(DataResult.Failure.NotFound::class)
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private suspend fun purge(statuses: Set<NotificationStatus>, before: kotlin.time.Instant, limit: Int) =
        (repository.purgeFinished(statuses, before, limit) as DataResult.Success).value

    private fun remainingIds() = transaction {
        NotificationOutboxTable.selectAll().map { it[NotificationOutboxTable.id].value }
    }

    private fun insertRow(
        status: NotificationStatus = PENDING,
        dueIn: Duration = (-1).minutes,
        age: Duration = Duration.ZERO,
        attempts: Int = 0,
        traceParent: String? = null,
    ): Long = transaction {
        val now = Clock.System.now()
        NotificationOutboxTable.insertAndGetId {
            it[type] = NotificationType.MOVIE_ADDED
            it[idempotencyKey] = Uuid.random().toString()
            it[targetKind] = NotificationTargetKind.TOPIC
            it[topic] = "dev-new-movies"
            it[payload] = StoredPush(title = "t", body = "b")
            it[this.status] = status
            it[this.attempts] = attempts
            it[nextAttemptAt] = now + dueIn
            it[createdAt] = now - age
            it[this.traceParent] = traceParent
            if (status == IN_FLIGHT) {                                   // chk_outbox_lease
                it[lockedBy] = "other"
                it[lockedUntil] = now + 1.minutes
            }
        }.value
    }
}