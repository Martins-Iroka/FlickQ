package com.martdev.flickq.config

import io.ktor.server.application.*

data class DatabaseConfig(
    val address: String = "",
    val user: String = "",
    val password: String = "",
    val maxOpenCon: Int = 0,
    val maxIdleCon: Int = 0,
    val maxIdleTime: Long = 0
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): DatabaseConfig {
            return DatabaseConfig(
                address = environment.getEnvValue("database.address"),
                user = environment.getEnvValue("database.user"),
                password = environment.getEnvValue("database.password"),
                maxOpenCon = environment.getEnvValue("database.maxOpenCons").toIntOrNull() ?: 10,
                maxIdleCon = environment.getEnvValue("database.maxIdleCons").toIntOrNull() ?: 10,
                maxIdleTime = environment.getEnvValue("database.maxIdleTime").toLongOrNull() ?: 5
            )
        }
    }
}
