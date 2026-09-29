package com.martdev.flickq.config

import io.ktor.server.application.ApplicationEnvironment

data class FcmConfig(
    val enabled: Boolean = false,
    val projectId: String = "",
    val credentials: String = "",
    val dryRun: Boolean = false,
    val topicPrefix: String = "dev-",
    val batchSize: Int = 100,
    val concurrency: Int = 16,
    val pollMs: Long = 1000,
    val baseMs: Long = 5000,
    val capMs: Long = 3_600_000,
    val maxAttempts: Int = 8
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): FcmConfig {
            val defaults = FcmConfig()
            return FcmConfig(
                enabled = environment.getEnvValue("fcm.enabled").toBoolean(),
                projectId = environment.getEnvValue("fcm.projectId"),
                credentials = environment.getEnvValue("fcm.credentials"),
                dryRun = environment.getEnvValue("fcm.dryRun").toBoolean(),
                topicPrefix = environment.getEnvValue("fcm.topicPrefix"),
                batchSize = environment.getEnvValue("fcm.worker.batchSize").toIntOrNull() ?: defaults.batchSize,
                concurrency = environment.getEnvValue("fcm.worker.concurrency").toIntOrNull() ?: defaults.concurrency,
                pollMs = environment.getEnvValue("fcm.worker.pollMs").toLongOrNull() ?: defaults.pollMs,
                baseMs = environment.getEnvValue("fcm.retry.baseMs").toLongOrNull() ?: defaults.baseMs,
                capMs = environment.getEnvValue("fcm.retry.capMs").toLongOrNull() ?: defaults.capMs,
                maxAttempts = environment.getEnvValue("fcm.retry.maxAttempts").toIntOrNull() ?: defaults.maxAttempts
            )
        }
    }
}