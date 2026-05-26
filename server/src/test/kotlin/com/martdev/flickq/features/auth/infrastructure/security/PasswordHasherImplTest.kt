package com.martdev.flickq.features.auth.infrastructure.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherImplTest {

    private val hasher = PasswordHasherImpl()

    @Test
    fun `hashPassword produces a verifiable hash`() {
        val hash = hasher.hashPassword("Correct-Horse-Battery-Staple-1!")

        assertTrue(hasher.verifyPassword("Correct-Horse-Battery-Staple-1!", hash))
    }

    @Test
    fun `verifyPassword returns false for wrong password`() {
        val hash = hasher.hashPassword("Correct-Horse-Battery-Staple-1!")

        assertFalse(hasher.verifyPassword("wrong-password", hash))
    }

    @Test
    fun `hashPassword uses a fresh salt each time (same input yields different hashes)`() {
        val password = "Correct-Horse-Battery-Staple-1!"
        val hash1 = hasher.hashPassword(password)
        val hash2 = hasher.hashPassword(password)

        assertNotEquals(hash1, hash2)
        assertTrue(hasher.verifyPassword(password, hash1))
        assertTrue(hasher.verifyPassword(password, hash2))
    }

    @Test
    fun `hashPassword encodes cost factor 12 in the produced hash`() {
        val hash = hasher.hashPassword("Correct-Horse-Battery-Staple-1!")

        val costPart = hash.split("$").getOrNull(2)
        assertEquals("12", costPart, "Expected cost factor 12 in hash, got: $hash")
    }

    @Test
    fun `hashPassword handles passwords at BCrypt's max length (72 bytes)`() {
        val longPassword = "A".repeat(72)
        val hash = hasher.hashPassword(longPassword)

        assertTrue(hasher.verifyPassword(longPassword, hash))
    }
}
