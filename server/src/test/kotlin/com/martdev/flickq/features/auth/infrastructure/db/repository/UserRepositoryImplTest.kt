package com.martdev.flickq.features.auth.infrastructure.db.repository

import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.auth.infrastructure.db.table.UserRefreshTokenTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserVerificationTable
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Testcontainers
class UserRepositoryImplTest {

    private val repository: UserRepository = UserRepositoryImpl()

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgresContainer.initPostgres()

        @JvmStatic
        @BeforeAll
        fun connectAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
        }
    }

    @BeforeEach
    fun cleanDb() {
        transaction {
            UserRefreshTokenTable.deleteAll()
            UserVerificationTable.deleteAll()
            UserTable.deleteAll()
        }
    }

    @AfterEach
    fun cleanAfter() {
        transaction {
            UserRefreshTokenTable.deleteAll()
            UserVerificationTable.deleteAll()
            UserTable.deleteAll()
        }
    }

    private fun freshEmail() = "user-${Random.nextLong(0, Long.MAX_VALUE)}@example.com"

    private suspend fun seedUser(email: String = freshEmail(), token: String = "vt-${Random.nextInt()}"): Long {
        val result = repository.saveUserAndVerificationToken(
            UserData(email = email, password = "password-hash"),
            token,
        )
        check(result is DataResult.Success) { "Failed to seed user: $result" }
        return result.value.id
    }

    private fun expiry(plus: Duration = 1.hours): LocalDateTime =
        Clock.System.now().plus(plus).toLocalDateTime(TimeZone.currentSystemDefault())

    @Test
    fun `saveUserAndVerificationToken creates a user row and returns id`() = runTest {
        val result = repository.saveUserAndVerificationToken(
            UserData(email = freshEmail(), password = "pw"),
            "verification-token",
        )

        assertTrue(result is DataResult.Success)
        assertTrue(result.value.id > 0)
    }

    @Test
    fun `saveUserAndVerificationToken with duplicate email returns UniqueViolation`() = runTest {
        val email = freshEmail()
        seedUser(email = email, token = "first-token")

        val second = repository.saveUserAndVerificationToken(
            UserData(email = email, password = "pw"),
            "second-token",
        )

        assertTrue(second is DataResult.Failure.UniqueViolation)
    }

    @Test
    fun `saveUserAndVerificationToken with duplicate verification token returns UniqueViolation`() = runTest {
        val token = "shared-token"
        seedUser(token = token)

        val second = repository.saveUserAndVerificationToken(
            UserData(email = freshEmail(), password = "pw"),
            token,
        )

        assertTrue(second is DataResult.Failure.UniqueViolation)
    }

    @Test
    fun `getUserById returns the user when present`() = runTest {
        val userId = seedUser()

        val result = repository.getUserById(userId)

        assertTrue(result is DataResult.Success)
        assertEquals(userId, result.value.id)
    }

    @Test
    fun `getUserById returns NotFound for non-existent id`() = runTest {
        val result = repository.getUserById(999_999L)

        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `getUserByEmail returns the user when present`() = runTest {
        val email = freshEmail()
        val userId = seedUser(email = email)

        val result = repository.getUserByEmail(email)

        assertTrue(result is DataResult.Success)
        assertEquals(userId, result.value.id)
    }

    @Test
    fun `getUserByEmail returns NotFound when missing`() = runTest {
        val result = repository.getUserByEmail("ghost@example.com")

        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `activateUser marks the user verified and consumes the token`() = runTest {
        val token = "activation-token"
        seedUser(token = token)

        val result = repository.activateUser(token)

        assertTrue(result is DataResult.Success)
        val second = repository.activateUser(token)
        assertTrue(second is DataResult.Failure.NotFound)
    }

    @Test
    fun `activateUser returns NotFound for invalid token`() = runTest {
        val result = repository.activateUser("never-existed")

        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `saveRefreshToken persists token for an existing user`() = runTest {
        val userId = seedUser()

        val result = repository.saveRefreshToken(userId, "rt-hash", expiry())

        assertTrue(result is DataResult.Success)
    }

    @Test
    fun `saveRefreshToken returns NotFound for unknown user id`() = runTest {
        val result = repository.saveRefreshToken(999_999L, "rt-hash", expiry())

        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `getUserIdAndRoleByRefreshToken returns the user when token is valid`() = runTest {
        val userId = seedUser()
        repository.saveRefreshToken(userId, "rt-hash", expiry())

        val result = repository.getUserIdAndRoleByRefreshToken("rt-hash")

        assertTrue(result is DataResult.Success)
        assertEquals(userId, result.value.id)
    }

    @Test
    fun `getUserIdAndRoleByRefreshToken returns NotFound for unknown token`() = runTest {
        val result = repository.getUserIdAndRoleByRefreshToken("never-existed")

        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `getUserIdAndRoleByRefreshToken returns NotFound for revoked token`() = runTest {
        val userId = seedUser()
        repository.saveRefreshToken(userId, "rt-hash", expiry())
        repository.revokeRefreshToken("rt-hash")

        val result = repository.getUserIdAndRoleByRefreshToken("rt-hash")

        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `revokeRefreshToken returns Success for an existing token`() = runTest {
        val userId = seedUser()
        repository.saveRefreshToken(userId, "rt-hash", expiry())

        assertTrue(repository.revokeRefreshToken("rt-hash") is DataResult.Success)
    }

    @Test
    fun `deleteExpiredRefreshToken removes only past-expiry rows`() = runTest {
        val userId = seedUser()
        val expired = Clock.System.now().minus(1.hours).toLocalDateTime(TimeZone.currentSystemDefault())
        val valid = expiry()
        repository.saveRefreshToken(userId, "expired-hash", expired)
        repository.saveRefreshToken(userId, "valid-hash", valid)

        repository.deleteExpiredRefreshToken()

        assertTrue(repository.getUserIdAndRoleByRefreshToken("expired-hash") is DataResult.Failure.NotFound)
        assertTrue(repository.getUserIdAndRoleByRefreshToken("valid-hash") is DataResult.Success)
    }

    @Test
    fun `deleteAndCreateVerificationToken replaces the existing token`() = runTest {
        val userId = seedUser(token = "old-token")

        val result = repository.deleteAndCreateVerificationToken("new-token", userId)

        assertTrue(result is DataResult.Success)
        assertTrue(repository.activateUser("old-token") is DataResult.Failure.NotFound)
        assertTrue(repository.activateUser("new-token") is DataResult.Success)
    }

    @Test
    fun `rotateRefreshToken atomically revokes old token and creates new one`() = runTest {
        val userId = seedUser()
        repository.saveRefreshToken(userId, "old-hash", expiry())

        val result = repository.rotateRefreshToken("old-hash", "new-hash", expiry())

        assertTrue(result is DataResult.Success)
        assertEquals(userId, result.value.id)
        assertTrue(repository.getUserIdAndRoleByRefreshToken("old-hash") is DataResult.Failure.NotFound)
        assertTrue(repository.getUserIdAndRoleByRefreshToken("new-hash") is DataResult.Success)
    }

    @Test
    fun `rotateRefreshToken returns NotFound for an unknown old token`() = runTest {
        val result = repository.rotateRefreshToken("never-existed", "new-hash", expiry())

        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `rotateRefreshToken returns NotFound for a previously revoked token (replay-attempt)`() = runTest {
        val userId = seedUser()
        repository.saveRefreshToken(userId, "rt-hash", expiry())
        repository.revokeRefreshToken("rt-hash")

        val result = repository.rotateRefreshToken("rt-hash", "new-hash", expiry())

        assertTrue(result is DataResult.Failure.NotFound)
        assertTrue(repository.getUserIdAndRoleByRefreshToken("new-hash") is DataResult.Failure.NotFound)
    }

    @Test
    fun `rotateRefreshToken returns NotFound for an expired token`() = runTest {
        val userId = seedUser()
        val pastExpiry = Clock.System.now().minus(1.minutes).toLocalDateTime(TimeZone.currentSystemDefault())
        repository.saveRefreshToken(userId, "rt-hash", pastExpiry)

        val result = repository.rotateRefreshToken("rt-hash", "new-hash", expiry())

        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `rotateRefreshToken issued tokens are distinct from old ones`() = runTest {
        val userId = seedUser()
        repository.saveRefreshToken(userId, "rt-old", expiry())

        val result = repository.rotateRefreshToken("rt-old", "rt-new", expiry())

        assertTrue(result is DataResult.Success)
        assertNotEquals("rt-old", "rt-new")
    }
}