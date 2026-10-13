package com.martdev.flickq.features.notification.infrastructure.db.table

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.auth.infrastructure.db.repository.UserRepositoryImpl
import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserVerificationTable
import com.martdev.flickq.features.notification.domain.model.NotificationEvent
import com.martdev.flickq.features.notification.domain.observability.NoopNotificationMetrics
import com.martdev.flickq.features.notification.domain.service.NotificationComposer
import com.martdev.flickq.features.notification.infrastructure.db.repository.NotificationOutboxWriter
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.notification.model.NotificationTargetKind
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

@Testcontainers
class NotificationOutboxTableTest {

    companion object {
        private lateinit var userRepo: UserRepository
        private lateinit var writer: NotificationOutboxWriter

        @Container
        val postgres = PostgresContainer.initPostgres()

        @JvmStatic
        @BeforeAll
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            userRepo = UserRepositoryImpl()
            val config = FcmConfig(false, "p", "", false, "dev-")
            val composer = NotificationComposer(config)
            writer = NotificationOutboxWriter(composer, NoopNotificationMetrics)
        }
    }

    @BeforeEach
    fun clean() {
        transaction {
            NotificationOutboxTable.deleteAll()
            UserVerificationTable.deleteAll()
            UserTable.deleteAll()
        }
    }

    @Test
    fun `a token row round-trips payload, pending tokens, enums and defaults`() {
        val stored = StoredPush(
            title = "New", body = "Dune",
            data = mapOf("k" to "v"), highPriority = true, collapseKey = "ck", ttlSeconds = 900,
            imageUrl = "img_url",
            androidChannelId = "bookings"
        )

        insert {
            it[targetKind] = NotificationTargetKind.TOKENS
            it[topic] = "dev-new-movies"
            it[pendingTokens] = listOf("a", "b")
            it[payload] = stored
        }

        val r = all().single()

        assertThat(r[NotificationOutboxTable.payload]).isEqualTo(stored)
        assertThat(r[NotificationOutboxTable.pendingTokens]).isEqualTo(listOf("a", "b"))
        assertThat(r[NotificationOutboxTable.type]).isEqualTo(NotificationType.MOVIE_ADDED)
        assertThat(r[NotificationOutboxTable.targetKind]).isEqualTo(NotificationTargetKind.TOKENS)
        assertThat(r[NotificationOutboxTable.status]).isEqualTo(NotificationStatus.PENDING)
        assertThat(r[NotificationOutboxTable.attempts]).isEqualTo(0)
        assertThat(r[NotificationOutboxTable.lockedBy]).isNull()
        assertThat(r[NotificationOutboxTable.lockedUntil]).isNull()
    }

    @Test
    fun `a user row stores null pending tokens`() = runTest {
        val userId = setupUser()
        insert {
            it[targetKind] = NotificationTargetKind.USER
            it[this.userId] = userId
        }

        val r = all().single()

        assertThat(r[NotificationOutboxTable.pendingTokens]).isNull()
    }

    @Test
    fun `a payload with an unknown JSON key still deserializes`() {
        transaction {
            exec("""
                INSERT INTO notification_outbox (type, idempotency_key, target_kind, topic, payload)
                VALUES ('MOVIE_ADDED', 'k1', 'TOPIC', 't', '{"title":"T","body":"B","someFutureField":42}')
            """.trimIndent())
        }
        val r = all().single()

        assertThat(r[NotificationOutboxTable.payload].title).isEqualTo("T")
    }

    @Test
    fun `enqueuing the same event twice leaves one row and reports the duplicate`() {
        val event = NotificationEvent.MovieAdded(7, "Dune", null)
        val first = transaction {
            writer.enqueueInCurrentTx(event)
        }
        val second = transaction {
            writer.enqueueInCurrentTx(event)
        }

        assertThat(first).isTrue()
        assertThat(second).isFalse()

        val size = all().size

        assertThat(size).isEqualTo(1)
    }

    @Test
    fun `the writer stores the composed payload and topic`() {
        val event = NotificationEvent.MovieAdded(7, "Dune", null)
        transaction {
            writer.enqueueInCurrentTx(event)
        }

        val r = all().single()

        assertThat(r[NotificationOutboxTable.idempotencyKey]).isEqualTo(
            "movie_added:7"
        )
        assertThat(r[NotificationOutboxTable.topic]).isEqualTo("dev-new-movies")
        assertThat(
            r[NotificationOutboxTable.payload].data["deeplink"]
        ).isEqualTo("flickq://movie/7")
    }

    @Test
    fun `an enqueue inside a rolled-back transaction leaves no row`() {
        runCatching {
            transaction {
                writer.enqueueInCurrentTx(NotificationEvent.MovieAdded(7, "Dune", null))
                error("business write failed")
            }
        }.onFailure {
            assertThat(it.message).isEqualTo("business write failed")
        }
        assertThat(all()).isEmpty()
    }

    // ---- chk_outbox_target ----

    @Test
    fun `a TOPIC row without a topic is rejected`() = assertEquals(
        "23514", sqlStateOf {
            insert {
                it[targetKind] = NotificationTargetKind.TOPIC
            }
        }
    )

    @Test
    fun `a TOPIC row with pending tokens is rejected`() = assertEquals(
        "23514", sqlStateOf {
            insert {
                it[targetKind] = NotificationTargetKind.TOPIC
                it[topic] = "t"
                it[pendingTokens] = listOf("a")
            }
        }
    )

    @Test
    fun `a USER row without a user is rejected`() = assertEquals(
        "23514", sqlStateOf {
            insert {
                it[targetKind] = NotificationTargetKind.USER
            }
        }
    )

    @Test
    fun `a TOKENS row without pending tokens is rejected`() = assertEquals(
        "23514", sqlStateOf {
            insert {
                it[targetKind] = NotificationTargetKind.TOKENS
            }
        }
    )

    // ---- chk_outbox_lease ----

    @Test
    fun `an IN_FLIGHT row without a lease is rejected`() = assertEquals(
        "23514", sqlStateOf {
            insert {
                it[targetKind] = NotificationTargetKind.TOPIC
                it[topic] = "t"
                it[status] = NotificationStatus.IN_FLIGHT
            }
        }
    )

    @Test
    fun `a PENDING row that carries a lease is rejected`() = assertEquals(
        "23514", sqlStateOf {
            insert {
                it[targetKind] = NotificationTargetKind.TOPIC
                it[topic] = "t"
                it[lockedBy] = "w1"
                it[lockedUntil] = Clock.System.now() + 1.minutes
            }
        }
    )

    @Test
    fun `an IN_FLIGHT row with a lease is accepted`() {
        insert {
            it[targetKind] = NotificationTargetKind.TOPIC
            it[topic] = "t"
            it[status] = NotificationStatus.IN_FLIGHT
            it[lockedBy] = "w1"
            it[lockedUntil] = Clock.System.now() + 1.minutes
        }

        assertThat(all().size).isEqualTo(1)
    }

    // ---- other constraints ----

    @Test
    fun `a negative attempts count is rejected`() = assertEquals(
        "23514", sqlStateOf {
            insert {
                it[targetKind] = NotificationTargetKind.TOPIC
                it[topic] = "t"
                it[attempts] = -1
            }
        }
    )

    @Test
    fun `deleting a user deletes their pending notifications`() = runTest {
        val userId = setupUser()
        insert {
            it[targetKind] = NotificationTargetKind.USER
            it[this.userId] = userId
        }
        transaction {
            UserVerificationTable.deleteWhere { UserVerificationTable.userId eq userId }
            UserTable.deleteWhere { UserTable.id eq userId }
        }

        assertThat(all()).isEmpty()
    }

    private suspend fun setupUser(): Long {
        val n = Uuid.random()
        return (userRepo.saveUserAndVerificationToken(
            UserData(email = "user_$n@test.com", password = "password"), "verify_$n"
        ) as DataResult.Success).value.id
    }

    private fun all() = transaction {
        NotificationOutboxTable.selectAll().toList()
    }

    private fun insert(body: NotificationOutboxTable.(InsertStatement<Number>) -> Unit) =
        transaction {
            NotificationOutboxTable.insert {
                it[type] = NotificationType.MOVIE_ADDED
                it[idempotencyKey] = Uuid.random().toString()
                it[payload] = StoredPush(title = "t", body = "b")
                body(it)
            }
        }

    private fun sqlStateOf(block: () -> Unit) =
        assertFailsWith<ExposedSQLException>(block = block).sqlState
}