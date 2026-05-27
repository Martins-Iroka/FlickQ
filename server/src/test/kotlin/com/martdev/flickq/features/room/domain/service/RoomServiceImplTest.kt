package com.martdev.flickq.features.room.domain.service

import com.martdev.flickq.features.room.domain.repository.RoomRepository
import com.martdev.flickq.room.model.Room
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
class RoomServiceImplTest {

    @MockK
    private lateinit var repository: RoomRepository

    private lateinit var service: RoomService

    @BeforeEach
    fun setup() {
        service = RoomServiceImpl(repository)
    }

    @Test
    fun `test create room successfully`() = runTest {
        coEvery {
            repository.createRoom(any())
        } returns DataResult.Success(Room())

        service.createRoom(Room())

        coVerify {
            repository.createRoom(any())
        }
    }

    @Test
    fun `get all rooms`() = runTest {
        coEvery {
            repository.getAllRooms()
        } returns DataResult.Success(
            listOf(
                Room()
            )
        )

        val rooms = service.getAllRooms()
        assertTrue(rooms.isNotEmpty())
        assertEquals(1, rooms.size)
    }

    @Test
    fun `test get room by Id`() = runTest {
        coEvery {
            repository.getRoomById(any())
        } returns DataResult.Success(Room(name = "Room 1"))

        val room = service.getRoomById(1)
        assertEquals("Room 1", room.name)
    }

    @Test
    fun `test update room`() = runTest {
        coEvery {
            repository.updateRoom(any())
        } returns DataResult.Success(Room(name = "Updated Room"))

        val room = service.updateRoom(Room())
        assertEquals("Updated Room", room.name)
    }

    @Test
    fun `test delete room`() = runTest {
        coEvery {
            repository.deleteRoom(any())
        } returns DataResult.Success(1)

        service.deleteRoom(1)

        coVerify {
            repository.deleteRoom(any())
        }
    }
}