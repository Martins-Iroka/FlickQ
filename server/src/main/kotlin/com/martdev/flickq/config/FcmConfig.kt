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
    val maxAttempts: Int = 8,
    val ratePerSecond: Int = 500,
    val leaseSeconds: Long = 60,
    val retentionSentDays: Int = 7,          // SENT + EXPIRED
    val retentionDeadDays: Int = 30,
    val tokenStaleDays: Int = 60,            // active tokens not seen for this long (Firebase staleness guidance)
    val tokenInvalidatedDays: Int = 30,      // already-invalidated tokens kept this long for debugging
) {

    override fun toString() =
        "FcmConfig(enabled=$enabled, projectId=$projectId, dryRun=$dryRun, topicPrefix=$topicPrefix, " +
                "batchSize=$batchSize, concurrency=$concurrency, pollMs=$pollMs, baseMs=$baseMs, capMs=$capMs, " +
                "maxAttempts=$maxAttempts, ratePerSecond=$ratePerSecond, leaseSeconds=$leaseSeconds, " +
                "credentials=${if (credentials.isBlank()) "<unset>" else "<redacted>"}), " +
                "retentionSentDays=$retentionSentDays, retentionDeadDays=$retentionDeadDays, " +
                "tokenStaleDays=$tokenStaleDays, tokenInvalidatedDays=$tokenInvalidatedDays"

    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): FcmConfig {
            val defaults = FcmConfig()
            return FcmConfig(
                enabled = environment.getEnvValue("fcm.enabled").toBoolean(),
                projectId = environment.getEnvValue("fcm.projectId"),
                credentials = environment.getEnvValue("fcm.credentials"),
                dryRun = environment.getEnvValue("fcm.dryRun").toBoolean(),
                topicPrefix = environment.getEnvValue("fcm.topicPrefix"),
                batchSize = environment.getEnvValue("fcm.worker.batchSize").toIntOrNull()
                    ?: defaults.batchSize,
                concurrency = environment.getEnvValue("fcm.worker.concurrency").toIntOrNull()
                    ?: defaults.concurrency,
                pollMs = environment.getEnvValue("fcm.worker.pollMs").toLongOrNull()
                    ?: defaults.pollMs,
                baseMs = environment.getEnvValue("fcm.retry.baseMs").toLongOrNull()
                    ?: defaults.baseMs,
                capMs = environment.getEnvValue("fcm.retry.capMs").toLongOrNull() ?: defaults.capMs,
                maxAttempts = environment.getEnvValue("fcm.retry.maxAttempts").toIntOrNull()
                    ?: defaults.maxAttempts,
                ratePerSecond = environment.getEnvValue("fcm.worker.ratePerSecond").toIntOrNull()
                    ?: defaults.ratePerSecond,
                leaseSeconds = environment.getEnvValue("fcm.worker.leaseSeconds").toLongOrNull()
                    ?: defaults.leaseSeconds,
                retentionSentDays = days(
                    environment,
                    "fcm.retention.sentDays",
                    defaults.retentionSentDays
                ),
                retentionDeadDays = days(
                    environment,
                    "fcm.retention.deadDays",
                    defaults.retentionDeadDays
                ),
                tokenStaleDays = days(
                    environment,
                    "fcm.retention.tokenStaleDays",
                    defaults.tokenStaleDays
                ),
                tokenInvalidatedDays = days(
                    environment,
                    "fcm.retention.tokenInvalidatedDays",
                    defaults.tokenInvalidatedDays
                ),
            )
        }
    }
}

private fun days(environment: ApplicationEnvironment, key: String, default: Int) =
    (environment.getEnvValue(key).toIntOrNull() ?: default).coerceAtLeast(1)