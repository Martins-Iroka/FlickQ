package com.martdev.flickq.features.room.domain.service

import com.martdev.flickq.features.room.domain.repository.SeatRepository
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.shared.domain.model.DataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class SeatServiceImplTest {

    @MockK
    private lateinit var repository: SeatRepository

    private lateinit var service: SeatService

    @BeforeEach
    fun setup() {
        service = SeatServiceImpl(repository)
    }

    @Test
    fun `create seats`() = runTest {
        coEvery {
            repository.createSeats(any())
        } returns DataResult.Success(listOf(Seat()))

        service.createSeats(listOf(Seat()))

        coVerify {
            repository.createSeats(any())
        }
    }

    @Test
    fun `get seats by room id`() = runTest {
        coEvery {
            repository.getSeatsByRoomId(any())
        } returns DataResult.Success(listOf(Seat()))

        val result = service.getSeatsByRoomId(1)
        assertTrue(result.isNotEmpty())
        assertEquals(1, result.size)
    }

    @Test
    fun `get seat by id`() = runTest {
        coEvery {
            repository.getSeatById(any())
        } returns DataResult.Success(Seat(rowLabel = "A"))

        val result = service.getSeatById(1)
        assertEquals("A", result.rowLabel)
    }
}