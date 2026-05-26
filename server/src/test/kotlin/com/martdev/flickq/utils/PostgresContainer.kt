package com.martdev.flickq.utils

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.postgresql.PostgreSQLContainer

object PostgresContainer {

    fun initPostgres() = PostgreSQLContainer(
        "postgres:16-alpine"
    ).apply {
        withDatabaseName("mrs")
        withUsername("test")
        withPassword("test")
    }

    fun connectToDBAndMigrate(postgres: PostgreSQLContainer) {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .load().migrate()

        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
    }
}