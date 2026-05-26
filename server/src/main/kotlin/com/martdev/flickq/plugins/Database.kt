package com.martdev.flickq.plugins

import com.martdev.flickq.config.DatabaseConfig
import com.martdev.flickq.shared.infrastruce.db.DatabaseFactory
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject

fun Application.configureDatabase() {
    val config by inject<DatabaseConfig>()

    DatabaseFactory.setupDatabase(config)
}