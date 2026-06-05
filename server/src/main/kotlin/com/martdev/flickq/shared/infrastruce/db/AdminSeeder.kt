package com.martdev.flickq.shared.infrastruce.db

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.config.SeedConfig
import com.martdev.flickq.features.auth.domain.security.PasswordHasher
import com.martdev.flickq.features.auth.infrastructure.db.table.UserEntity
import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger

/**
 * Bootstrap-admin seeder, run once on startup. Industry-standard "first admin" handling:
 * credentials come from the environment (never committed), the password is hashed with the app's
 * own [PasswordHasher] so the account is guaranteed loginable, and the insert is idempotent —
 * skipped when [SeedConfig.adminEmail] is unset or the admin already exists. Sets `isVerified`
 * since login rejects unverified accounts.
 */
suspend fun seedAdminUser(config: SeedConfig, hasher: PasswordHasher, logger: Logger) {
    val email = config.adminEmail ?: return
    val password = config.adminPassword
    if (password.isNullOrBlank()) {
        logger.warn("SEED_ADMIN_EMAIL is set but SEED_ADMIN_PASSWORD is missing/blank — skipping admin seed")
        return
    }

    suspendTransaction {
        val alreadyExists = UserEntity.find { UserTable.email eq email }.firstOrNull() != null
        if (alreadyExists) {
            logger.info("Admin '{}' already exists — seed skipped", email)
            return@suspendTransaction
        }
        UserEntity.new {
            this.email = email
            this.password = hasher.hashPassword(password)
            this.isVerified = true
            this.role = Role.ADMIN
        }
        logger.info("Seeded verified ADMIN user '{}'", email)
    }
}
