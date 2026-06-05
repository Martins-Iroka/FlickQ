package com.martdev.flickq.plugins

import com.martdev.flickq.config.DatabaseConfig
import com.martdev.flickq.config.SeedConfig
import com.martdev.flickq.features.auth.domain.security.PasswordHasher
import com.martdev.flickq.shared.infrastruce.db.DatabaseFactory
import com.martdev.flickq.shared.infrastruce.db.seedAdminUser
import io.ktor.server.application.Application
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject

fun Application.configureDatabase() {
    val config by inject<DatabaseConfig>()

    DatabaseFactory.setupDatabase(config)
    // Optional bootstrap admin from SEED_ADMIN_* env (no-op unless set); runs once after connect.
    val seedConfig by inject<SeedConfig>()
    val passwordHasher by inject<PasswordHasher>()
    runBlocking { seedAdminUser(seedConfig, passwordHasher, environment.log) }
}