package com.martdev.flickq.features.notification.infrastructure.db.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.auth.infrastructure.db.repository.UserRepositoryImpl
import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserVerificationTable
import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.infrastructure.db.table.DeviceTokenTable
import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Testcontainers
class DeviceTokenRepositoryImplTest {

    companion object {
        private lateinit var repository: DeviceTokenRepository
        private lateinit var userRepo: UserRepository
        private const val TOKEN = "26e4b632-cd1a-4655-8635-c5c1569a7b22"
        private val dispatcher = Dispatchers.IO

        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            repository = DeviceTokenRepositoryImpl()
            userRepo = UserRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            DeviceTokenTable.deleteAll()
            UserVerificationTable.deleteAll()
            UserTable.deleteAll()
        }
    }

    @Test
    fun `device upsert is called and returns saved device token`() =
        runTest {
            val userID = setupUser()

            val result = repository.upsert(
                userID, TOKEN, DevicePlatform.ANDROID, "1.0.0",
                "en-NG"
            )

            assertTrue(result is DataResult.Success, result.toString())
            assertEquals(TOKEN, result.value.token)
            assertEquals(userID, result.value.userId)
        }

    @Test
    fun `upsert throws a db exception for short token or invalid token`() = runTest {
        val userId = setupUser()

        val result = repository.upsert(
            userId, "invalid_token", DevicePlatform.ANDROID, "1.0",
            "en-NG"
        )

        assertThat(result).isInstanceOf(DataResult.Failure.UnknownError::class)
    }

    @Test
    fun `concurrent upserts of the same token never fail and leave one row`() = runTest {
        val userId = setupUser()

        val results = (1..20).map {
            async {
                repository.upsert(userId, TOKEN, DevicePlatform.ANDROID, "1.0.0", "en-NG")
            }
        }.awaitAll()

        assertThat(results.all { it is DataResult.Success }).isTrue()
        val count = transaction {
            DeviceTokenTable.selectAll().where { DeviceTokenTable.token eq TOKEN }.count()
        }
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `activeTokensFor returns only non-invalidated tokens, newest first`() = runTest {

        val userId = setupUser()
        val tokens = seedTokens(userId, 4)

        val invalidToken = tokens[2]

        repository.invalidate(listOf(invalidToken))

        val result = repository.activeTokensFor(userId)

        assertTrue(result is DataResult.Success, result.toString())
        assertThat(result.value).containsExactly(
            *tokens.filter { it != invalidToken }.asReversed().toTypedArray()
        )
    }

    @Test
    fun `with 12 registered tokens, activeTokensFor returns the 10 most recent`() = runTest {
        val userId = setupUser()
        val tokens = seedTokens(userId, 12)

        val activeTokens = repository.activeTokensFor(userId)

        assertTrue(activeTokens is DataResult.Success)
        assertThat(activeTokens.value).containsExactly(
            *tokens.takeLast(10).asReversed().toTypedArray()
        )
    }

    @Test
    fun `activeTokenFor returns an empty list for a user with no token`() = runTest {
        val userId = setupUser()

        val emptyToken = repository.activeTokensFor(userId)

        assertTrue(emptyToken is DataResult.Success)
        assertThat(emptyToken.value).isEmpty()
    }

    @Test
    fun `activeTokenFor returns an empty list for an invalid userId`() = runTest {
        val invalidUserId = 999_999L

        val emptyToken = repository.activeTokensFor(invalidUserId)

        assertTrue(emptyToken is DataResult.Success)
        assertThat(emptyToken.value).isEmpty()
    }

    @Test
    fun `for invalidate, calling it twice returns 1, then 0, and invalidated_at keeps the first timestamp`() =
        runTest {
            val userId = setupUser()
            val token = seedTokens(userId, 1, false)

            val first = repository.invalidate(token)
            val stampAfterFirst = invalidatedAt(token)

            val second = repository.invalidate(token)
            val stampAfterSecond = invalidatedAt(token)

            assertTrue(first is DataResult.Success)
            assertThat(first.value).isEqualTo(1)
            assertTrue(second is DataResult.Success)
            assertThat(second.value).isEqualTo(0)
            assertThat(stampAfterFirst).isNotNull()
            assertThat(stampAfterSecond).isEqualTo(stampAfterFirst)
        }

    @Test
    fun `calling invalidate with an empty list returns 0 and doesn't touch the DB`() = runTest {

        val result = repository.invalidate(emptyList())

        assertThat(result).isInstanceOf(DataResult.Success::class)
        assertThat((result as DataResult.Success).value).isEqualTo(0)
    }

    @Test
    fun `calling invalidate with 1_500 tokens across chunks invalidates all of them`() = runTest {
        val userId = setupUser()

        val tokens = List(1_500) {
            Uuid.random().toString()
        }

        transaction {
            DeviceTokenTable.batchInsert(tokens) { token ->
                this[DeviceTokenTable.userId] = userId
                this[DeviceTokenTable.token] = token
                this[DeviceTokenTable.platform] = DevicePlatform.ANDROID
                this[DeviceTokenTable.appVersion] = "1.0"
                this[DeviceTokenTable.locale] = "en-NG"
            }
        }

        val resultCount = repository.invalidate(tokens)

        val activeToken = repository.activeTokensFor(userId)

        assertTrue(resultCount is DataResult.Success)
        assertThat(resultCount.value).isEqualTo(1_500)

        assertTrue(activeToken is DataResult.Success)
        assertThat(activeToken.value).isEmpty()
    }

    @Test
    fun `invalidateForUser with owner, returns true and sets invalidated_at`() = runTest {
        val userId = setupUser()

        val token = seedTokens(userId, 1, false)

        val userInvalidated = repository.invalidateForUser(userId, token.single())

        val activeToken = repository.activeTokensFor(userId)

        val stampForInvalidatedToken = invalidatedAt(token)

        assertTrue(userInvalidated is DataResult.Success)
        assertThat(userInvalidated.value).isTrue()
        assertTrue(activeToken is DataResult.Success)
        assertThat(activeToken.value).isEmpty()
        assertThat(stampForInvalidatedToken).isNotNull()
    }

    @Test
    fun `invalidateForUser with a different user, returns false and the row is unchanged`() =
        runTest {
            val userId = setupUser()
            val userId2 = setupUser()

            val token = seedTokens(userId, 1, false)

            val userNotInvalidated = repository.invalidateForUser(userId2, token.single())

            val activeToken = (repository.activeTokensFor(userId) as DataResult.Success).value

            val stamp = invalidatedAt(token)

            assertTrue(userNotInvalidated is DataResult.Success)
            assertThat(userNotInvalidated.value).isFalse()
            assertThat(activeToken).isNotEmpty()
            assertThat(stamp).isNull()
        }

    @Test
    fun `pruneStale deletes a stale active row (61d) and returns its token`() = runTest {
        val userId = setupUser()
        val now = Clock.System.now()
        val staleDate = now.minus(61.days)
        val tokens = seedTokens(userId, 5, false)

        transaction {
            DeviceTokenTable.update(where = {
                DeviceTokenTable.token inList tokens.take(2)
            }) {
                it[lastSeenAt] = staleDate
            }
        }

        val result = repository.pruneStale(now.minus(60.days), now.minus(30.days))

        assertTrue(result is DataResult.Success)
        assertThat(result.value.deleted).isEqualTo(2)
        assertThat(result.value.activeTokens).containsExactly(tokens[0], tokens[1])
        assertThat(rowCount()).isEqualTo(0)
    }

    @Test
    fun `pruneStale deletes a long-dead row (31d invalidated) and does not return its token`() =
        runTest {
            val userId = setupUser()
            val now = Clock.System.now()
            val invalidDate = now.minus(31.days)
            seedTokens(userId, 2, false)

            transaction {
                DeviceTokenTable.update {
                    it[invalidatedAt] = invalidDate
                }
            }

            val result = repository.pruneStale(now.minus(60.days), now.minus(30.days))

            val rowCount = rowCount()

            assertTrue(result is DataResult.Success)
            assertThat(result.value.deleted).isEqualTo(2)
            assertThat(result.value.activeTokens).isEmpty()
            assertThat(rowCount).isEqualTo(0)
        }

    @Test
    fun `pruneStale leaves a fresh active row alone`() = runTest {
        val userId = setupUser()
        seedTokens(userId, 5)
        val now = Clock.System.now()

        val result = repository.pruneStale(now - 60.days, now.minus(30.days))

        assertTrue(result is DataResult.Success)
        assertThat(result.value.deleted).isEqualTo(0)
        assertThat(result.value.activeTokens).isEmpty()
        assertThat(rowCount()).isEqualTo(5)
    }

    @Test
    fun `pruneStale takes 3 calls to reach empty with limit = 2`() = runTest {
        val userId = setupUser()
        val now = Clock.System.now()
        val invalidDate = now.minus(61.days)
        val tokens = seedTokens(userId, 5, false)

        transaction {
            DeviceTokenTable.update {
                it[lastSeenAt] = invalidDate
            }
        }

        suspend fun prune() =
            (repository.pruneStale(now - 60.days, now - 30.days, limit = 2) as DataResult.Success).value

        val calls = buildList {
            repeat(4) {
                add(prune() to rowCount())
            }
        }

        println(calls.joinToString("\n"))

        assertThat(calls.map { it.first.deleted }).containsExactly(2, 2, 1, 0)
        assertThat(calls.map { it.second }).containsExactly(3L, 1L, 0L, 0L)
        assertThat(calls[0].first.activeTokens).containsExactly(tokens[0], tokens[1])
    }

    @Test
    fun `a row whose last_seen_at is bumped between select and delete survives the re-check`() =
        runTest {
            val userId = setupUser()
            val now = Clock.System.now()
            val tokens = seedTokens(userId, 2, isLastSeenNeeded = false)

            transaction {
                addLogger(StdOutSqlLogger)
                DeviceTokenTable.update {
                    it[lastSeenAt] = now.minus(61.days)
                }
            }
            val survivor = tokens.first()

            val lockHeld = CountDownLatch(1)
            val commitBump = CountDownLatch(1)

            val bumper = async(Dispatchers.IO) {
                transaction {
                    addLogger(StdOutSqlLogger)
                    DeviceTokenTable.update(where = {
                        DeviceTokenTable.token eq survivor
                    }) {
                        it[lastSeenAt] = CurrentTimestamp
                    }
                    lockHeld.countDown()
                    commitBump.await()
                }
            }
            lockHeld.await()

            val prune = async {
                repository.pruneStale(now.minus(60.days), now.minus(30.days))
            }

            withContext(Dispatchers.IO) {
                awaitBlockedOnRowLock()
            }
            println("We are here now")
            commitBump.countDown()
            bumper.await()

            val result = prune.await()

            assertTrue(result is DataResult.Success, result.toString())
            assertThat(result.value.deleted).isEqualTo(1)
            assertThat(result.value.activeTokens).containsExactly(tokens.last())

            val savedSurvivorToken = transaction {
                addLogger(StdOutSqlLogger)
                DeviceTokenTable.selectAll().map {
                    it[DeviceTokenTable.token]
                }
            }

            assertThat(savedSurvivorToken).containsExactly(survivor)
        }

    private fun awaitBlockedOnRowLock() {
        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            val waiting = transaction {
                addLogger(StdOutSqlLogger)
                exec("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND state = 'active'") { rs ->
                    rs.next(); rs.getInt(1)
                }
            } ?: 0
            println("See waiting time $waiting")
            if (waiting > 0) return
            Thread.sleep(25)
        }
        error("pruneStale never blocked on the row lock")
    }

    private suspend fun setupUser(): Long {
        val n = Uuid.random()
        return (userRepo.saveUserAndVerificationToken(
            UserData(email = "user_$n@test.com", password = "password"),
            "verify_token_$n"
        ) as DataResult.Success).value.id
    }

    private suspend fun seedTokens(
        userId: Long,
        count: Int,
        isLastSeenNeeded: Boolean = true
    ): List<String> {
        val now = Clock.System.now()
        val tokens = List(count) {
            Uuid.random().toString()
        }
        tokens.forEach { repository.upsert(userId, it, DevicePlatform.ANDROID, "1.0", "en-NG") }
        if (isLastSeenNeeded) {
            transaction {
                tokens.forEachIndexed { index, token ->
                    DeviceTokenTable.update({
                        DeviceTokenTable.token eq token
                    }) {
                        it[lastSeenAt] = now - (count - index).hours
                    }
                }
            }
        }

        return tokens
    }

    private fun invalidatedAt(token: List<String>): Instant? {
        return transaction {
            DeviceTokenTable.select(DeviceTokenTable.invalidatedAt)
                .where { DeviceTokenTable.token eq token.single() }
                .single()[DeviceTokenTable.invalidatedAt]
        }
    }

    private fun rowCount(): Long = transaction {
        DeviceTokenTable.selectAll().count()
    }
}