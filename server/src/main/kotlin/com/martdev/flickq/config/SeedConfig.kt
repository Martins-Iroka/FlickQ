package com.martdev.flickq.config

/**
 * Optional bootstrap-admin credentials, supplied at deploy time via the environment and never
 * committed. Read straight from the environment (OS env vars, or system properties populated by
 * dotenv) rather than referenced in `application.yaml`, so the secret leaves no trace in version
 * control. When both fields are present the startup seeder creates a verified ADMIN if one doesn't
 * already exist; when absent (the default) seeding is a no-op.
 * See [com.martdev.flickq.shared.infrastruce.db.seedAdminUser].
 */
data class SeedConfig(
    val adminEmail: String?,
    val adminPassword: String?,
) {
    companion object {
        fun fromEnvironment(): SeedConfig {
            fun read(key: String) =
                (System.getenv(key) ?: System.getProperty(key))?.takeIf { it.isNotBlank() }
            return SeedConfig(
                adminEmail = read("SEED_ADMIN_EMAIL"),
                adminPassword = read("SEED_ADMIN_PASSWORD"),
            )
        }
    }
}
