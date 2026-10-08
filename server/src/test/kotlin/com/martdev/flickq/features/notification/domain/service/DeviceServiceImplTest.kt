package com.martdev.flickq.features.notification.domain.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.martdev.flickq.config.FcmConfig
import com.martdev.flickq.features.notification.domain.model.OutboxDraft
import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.notification.model.NotificationType
import com.martdev.flickq.notification.model.RegisterDeviceRequest
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Clock

@ExtendWith(MockKExtension::class)
class DeviceServiceImplTest {
    @MockK
    lateinit var devices: DeviceTokenRepository

    @MockK
    lateinit var outbox: NotificationOutboxRepository

    val config = FcmConfig(false, "p", "", false, "dev-")
    private val composer = NotificationComposer(config)
    private lateinit var service: DeviceService
    private val request =
        RegisterDeviceRequest("t".repeat(30), DevicePlatform.ANDROID, "1.0", "en-NG")
    private val saved = DeviceToken(
        1, 42, request.token, DevicePlatform.ANDROID, "1.0", "en-NG",
        Clock.System.now(), Clock.System.now(), null
    )

    @BeforeEach
    fun setup() {
        service = DeviceServiceImpl(devices, outbox, composer)
        coEvery { outbox.enqueue(any()) } returns DataResult.Success(true)
    }

    @Test
    fun `register upserts then enqueues a topic subscription for that token`() = runTest {
        coEvery {
            devices.upsert(42, request.token, DevicePlatform.ANDROID, "1.0", "en-NG")
        } returns DataResult.Success(saved)

        val draft = slot<OutboxDraft>()
        coEvery { outbox.enqueue(capture(draft)) } returns DataResult.Success(true)

        val result = service.register(42, request)

        assertThat(result).isEqualTo(DataResult.Success(saved))

        assertThat(draft.captured.type).isEqualTo(NotificationType.TOPIC_SUBSCRIBE)
        assertThat(draft.captured.pendingTokens).isEqualTo(listOf(request.token))
    }

    @Test
    fun `a failed enqueue does not fail registration`() = runTest {
        coEvery {
            devices.upsert(any(), any(), any(), any(), any())
        } returns DataResult.Success(saved)
        coEvery {
            outbox.enqueue(any())
        } returns DataResult.Failure.UnknownError("db down")

        val result = service.register(42, request)
        assertThat(result).isInstanceOf(DataResult.Success::class)
    }

    @Test
    fun `a failed upsert enqueues nothing`() = runTest {
        coEvery {
            devices.upsert(any(), any(), any(), any(), any())
        } returns DataResult.Failure.UniqueViolation

        val result = service.register(42, request)

        assertThat(result).isInstanceOf(DataResult.Failure::class)
        coVerify(exactly = 0) { outbox.enqueue(any()) }
    }

    @Test
    fun `unregister enqueues an unsubscribe only when it really invalidated the owner's token`() = runTest {
        coEvery {
            devices.invalidateForUser(42, request.token)
        } returns DataResult.Success(true)

        service.unregister(42, request.token)

        coVerify(exactly = 1) {
            outbox.enqueue(match { it.type == NotificationType.TOPIC_UNSUBSCRIBE })
        }
    }

    @Test
    fun `unregister of someone else's or an already dead token is a quiet success with no enqueue`() = runTest {
        coEvery { devices.invalidateForUser(42, request.token) } returns DataResult.Success(false)

        val result = service.unregister(42, request.token)

        assertThat(result).isEqualTo(DataResult.Success(Unit))
        coVerify(exactly = 0) {
            outbox.enqueue(any())
        }
    }
}