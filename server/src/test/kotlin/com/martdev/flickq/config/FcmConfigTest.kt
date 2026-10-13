package com.martdev.flickq.config

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.config.MapApplicationConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class FcmConfigTest {
    private fun env(vararg values: Pair<String, String>) = mockk<ApplicationEnvironment> {
        every { config } returns MapApplicationConfig(*values)
    }

    @Test
    fun `toString never contains the credential`() {
        val secret = "eyJ0eXBlIjoic2VydmljZV9hY2NvdW50In0="   // synthetic
        val s = FcmConfig(credentials = secret).toString()
        assertThat(s).doesNotContain(secret)
        assertThat(s).contains("credentials=<redacted>")
    }

    @Test
    fun `blank credential is reported as unset`() =
        assertThat(FcmConfig().toString()).contains("credentials=<unset>")

    @Test
    fun `missing retention keys fall back to defaults`() {
        val c = FcmConfig.fromEnvironment(env())
        assertThat(listOf(c.retentionSentDays, c.retentionDeadDays, c.tokenStaleDays, c.tokenInvalidatedDays))
            .isEqualTo(listOf(7, 30, 60, 30))
    }

    @Test
    fun `zero or negative days are clamped to one`() {
        val c = FcmConfig.fromEnvironment(env("fcm.retention.sentDays" to "0", "fcm.retention.deadDays" to "-5"))
        assertThat(c.retentionSentDays).isEqualTo(1)
        assertThat(c.retentionDeadDays).isEqualTo(1)
    }

    @Test
    fun `toString shows retention but never the credential`() {
        val s = FcmConfig(credentials = "c2VjcmV0").toString()
        assertThat(s).contains("retentionDeadDays=30")
        assertThat(s).doesNotContain("c2VjcmV0")
    }
}