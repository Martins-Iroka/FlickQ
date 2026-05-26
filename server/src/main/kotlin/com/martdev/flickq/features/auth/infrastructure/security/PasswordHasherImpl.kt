package com.martdev.flickq.features.auth.infrastructure.security

import at.favre.lib.crypto.bcrypt.BCrypt
import com.martdev.flickq.features.auth.domain.security.PasswordHasher
import org.koin.core.annotation.Single

@Single
class PasswordHasherImpl : PasswordHasher {
    private val cost = 12
    override fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(cost, password.toCharArray())
    }

    override fun verifyPassword(plainPassword: String, hashedPassword: String): Boolean {
        return BCrypt.verifyer()
            .verify(plainPassword.toCharArray(), hashedPassword)
            .verified
    }
}