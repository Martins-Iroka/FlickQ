package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.shared.domain.exception.ConflictException
import com.martdev.flickq.shared.domain.exception.NotFoundException
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class OutboxAdminServiceImplTest {

    @MockK
    lateinit var outbox: NotificationOutboxRepository

    private val service by lazy { OutboxAdminServiceImpl(outbox) }

    @Test
    fun `list caps the page at 100`() = runTest {
        coEvery { outbox.list(null, 100, 0) } returns DataResult.Success(emptyList())

        service.list(null, 5_000, 0)

        coVerify { outbox.list(null, 100, 0) }
    }

    @Test
    fun `retry succeeds for a DEAD row`() = runTest {
        coEvery { outbox.requeueDead(9) } returns DataResult.Success(Unit)

        service.retry(9, adminId = 1)
    }

    @Test
    fun `retry surfaces not found and conflict`() = runTest {
        coEvery { outbox.requeueDead(1) } returns DataResult.Failure.NotFound("missing")
        coEvery { outbox.requeueDead(2) } returns DataResult.Failure.Conflict("SENT")

        assertFailsWith<NotFoundException> { service.retry(1, 1) }
        assertFailsWith<ConflictException> { service.retry(2, 1) }
    }
}