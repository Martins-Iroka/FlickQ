package com.martdev.flickq.config

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import org.junit.jupiter.api.Test

class FcmConfigTest {
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
}