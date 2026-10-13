package com.martdev.flickq.features.notification.infrastructure.fcm

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import com.martdev.flickq.config.FcmConfig
import org.junit.jupiter.api.Test

class FcmModulePoolClampTest {
    @Test
    fun `concurrency above pool minus headroom is clamped`() =
        assertThat(clampToPool(FcmConfig(concurrency = 16), poolSize = 10).concurrency).isEqualTo(6)

    @Test
    fun `concurrency within budget is untouched`() {
        val c = FcmConfig(concurrency = 4)
        assertThat(clampToPool(c, poolSize = 10)).isSameInstanceAs(c)
    }

    @Test
    fun `a tiny pool still allows one worker`() =
        assertThat(clampToPool(FcmConfig(concurrency = 16), poolSize = 2).concurrency).isEqualTo(1)

    @Test
    fun `unknown pool size leaves config alone`() =
        assertThat(clampToPool(FcmConfig(concurrency = 16), poolSize = null).concurrency).isEqualTo(16)
}