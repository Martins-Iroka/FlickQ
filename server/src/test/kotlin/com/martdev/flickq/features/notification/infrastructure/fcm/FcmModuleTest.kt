package com.martdev.flickq.features.notification.infrastructure.fcm

import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.service.FcmSender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.error.InstanceCreationException
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.inject
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FcmModuleTest : KoinTest {

    val fcmSender: FcmSender by inject()

    @BeforeEach
    fun cleanUp() = stopKoin()

    @Test
    fun `fcm sender instance should be logging fcm sender`() {
        startKoin {
            modules(
                module {
                    single {
                        FcmConfig()
                    }
                },
                fcmModule
            )
        }

        val fcmConfig = get<FcmConfig>()

        assertNotNull(fcmConfig)
        assertFalse(fcmConfig.enabled)
        assertTrue(fcmSender is LoggingFcmSender)
    }

    @Test
    fun `fcm sender instance should be firebase provider but fails`() {
        assertThrows<InstanceCreationException> {
            startKoin {
                modules(
                    module {
                        single {
                            FcmConfig(enabled = true)
                        }
                    },
                    fcmModule
                )
            }
        }

        val fcmConfig = get<FcmConfig>()

        assertNotNull(fcmConfig)
        assertTrue(fcmConfig.enabled)
    }
}