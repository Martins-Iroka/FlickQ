package com.martdev.flickq.features.notification.infrastructure.db.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.service.NotificationComposer
import com.martdev.flickq.features.notification.infrastructure.db.table.NotificationOutboxTable
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.utils.PostgresContainer
import com.martdev.flickq.utils.RecordingNotificationMetrics
import com.martdev.flickq.utils.TestNotifications
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class NotificationOutboxWriterMetricsTest {

    companion object {
        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connect() = PostgresContainer.connectToDBAndMigrate(postgres)
    }

    private val metrics = RecordingNotificationMetrics()
    private val writer = NotificationOutboxWriter(NotificationComposer(TestNotifications.config), metrics)

    @BeforeEach
    fun clean() {
        transaction { NotificationOutboxTable.deleteAll() }
    }

    private fun movie(id: Long) = NotificationEvent.MovieAdded(id, "Dune", null)   // TOPIC target: no FK rows needed

    @Test
    fun `an enqueue is counted once and only after commit`() {
        transaction {
            assertThat(writer.enqueueInCurrentTx(movie(1))).isTrue()
            assertThat(metrics.enqueued).isEmpty()                       // still inside the tx
        }
        assertThat(metrics.enqueued).containsExactly(NotificationType.MOVIE_ADDED)
    }

    @Test
    fun `a transaction that throws is not counted and leaves no row`() {
        runCatching {
            transaction {
                writer.enqueueInCurrentTx(movie(2))
                error("business write failed")
            }
        }

        assertThat(metrics.enqueued).isEmpty()
        assertThat(transaction { NotificationOutboxTable.selectAll().count() }).isEqualTo(0L)
    }

    @Test
    fun `rollback followed by the block's commit is not counted`() {
        // The shape withSuspendTransaction produces on a Failure: rollback(), then the block returns and commits.
        transaction {
            writer.enqueueInCurrentTx(movie(3))
            rollback()
        }

        assertThat(metrics.enqueued).isEmpty()
    }

    @Test
    fun `a duplicate idempotency key is not counted`() {
        transaction { writer.enqueueInCurrentTx(movie(4)) }
        transaction { assertThat(writer.enqueueInCurrentTx(movie(4))).isFalse() }

        assertThat(metrics.enqueued).containsExactly(NotificationType.MOVIE_ADDED)
    }
}