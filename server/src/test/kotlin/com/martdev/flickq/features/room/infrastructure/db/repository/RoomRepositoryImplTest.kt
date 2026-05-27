package com.martdev.flickq.features.room.infrastructure.db.repository

import com.martdev.flickq.features.room.domain.repository.RoomRepository
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Testcontainers
class RoomRepositoryImplTest {

    companion object {
        private lateinit var repo: RoomRepository

        @Container
        val postgres = PostgresContainer.initPostgres()

        @JvmStatic
        @BeforeAll
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            repo = RoomRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            RoomTable.deleteAll()
        }
    }

    @Test
    fun `create room successfully`() = runTest {
        val room = Room(name = "Room 1", rows = 5, columns = 10)

        val result = repo.createRoom(room)

        assertTrue(result is DataResult.Success)
        val savedRoom = result.value
        assertEquals(room.name, savedRoom.name)
    }

    @Test
    fun `get all rooms should return list of rooms`() = runTest {
        val room1 = Room(name = "Room 2", rows = 10, columns = 10)
        val room2 = Room(name = "Room 3", rows = 5, columns = 20)

        repo.createRoom(room1)
        repo.createRoom(room2)

        val result = repo.getAllRooms()

        assertTrue(result is DataResult.Success)
        assertEquals(2, result.value.size)
    }

    @Test
    fun `get all rooms should return empty list`() = runTest {
        val result = repo.getAllRooms()
        assertTrue(result is DataResult.Success)
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `get room by id should return a room`() = runTest {
        val room = Room(name = "Room 4", rows = 10, columns = 20)

        val result = repo.createRoom(room)
        assertTrue(result is DataResult.Success)

        val savedRoomId = result.value.id

        val retrievedRoomResult = repo.getRoomById(savedRoomId)

        assertTrue(retrievedRoomResult is DataResult.Success)
        val retrievedRoom = retrievedRoomResult.value
        assertEquals(room.name, retrievedRoom.name)
    }

    @Test
    fun `get room by invalid id should not be found`() = runTest {
        val invalidResult = repo.getRoomById(Random.nextLong())
        assertTrue(invalidResult is DataResult.Failure.NotFound)
        assertEquals("Room not found", invalidResult.errorMessage)
    }

    @Test
    fun `update room should return updated room`() = runTest {
        val room = Room(name = "Room 5", rows = 5, columns = 10)

        val savedRoomResult = repo.createRoom(room)
        assertTrue(savedRoomResult is DataResult.Success)

        val savedRoom = savedRoomResult.value

        val updatedRoomResult = repo.updateRoom(savedRoom.copy(name = "Room 55"))
        assertTrue(updatedRoomResult is DataResult.Success)
        val updatedRoom = updatedRoomResult.value
        assertNotEquals(savedRoom.name, updatedRoom.name)
    }

    @Test
    fun `update room should return not found`() = runTest {
        val invalidResult = repo.updateRoom(Room(id = 55))
        assertTrue(invalidResult is DataResult.Failure.NotFound)
        assertEquals("Room not found for update", invalidResult.errorMessage)
    }

    @Test
    fun `delete room should return count of deleted row`() = runTest {
        val room = Room(name = "Room 6", rows = 10, columns = 5)

        val savedRoomResult = repo.createRoom(room)
        assertTrue(savedRoomResult is DataResult.Success)

        val roomId = savedRoomResult.value.id
        val deletedRowResult = repo.deleteRoom(roomId)

        assertTrue(deletedRowResult is DataResult.Success)
        assertEquals(1, deletedRowResult.value)
    }

    @Test
    fun `delete room should return not found`() = runTest {
        val invalidResult = repo.deleteRoom(Random.nextLong())
        assertTrue(invalidResult is DataResult.Failure.NotFound)
    }
}