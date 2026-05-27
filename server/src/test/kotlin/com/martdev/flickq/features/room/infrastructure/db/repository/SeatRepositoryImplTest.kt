package com.martdev.flickq.features.room.infrastructure.db.repository

import com.martdev.flickq.features.room.domain.repository.RoomRepository
import com.martdev.flickq.features.room.domain.repository.SeatRepository
import com.martdev.flickq.features.room.infrastructure.db.table.RoomTable
import com.martdev.flickq.features.room.infrastructure.db.table.SeatTable
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.room.model.Seat
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
import kotlin.test.assertTrue

@Testcontainers
class SeatRepositoryImplTest {

    companion object {
        private lateinit var repo: SeatRepository
        private lateinit var roomRepo: RoomRepository

        @Container
        val postgres = PostgresContainer.initPostgres()

        @BeforeAll
        @JvmStatic
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            repo = SeatRepositoryImpl()
            roomRepo = RoomRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            SeatTable.deleteAll()
            RoomTable.deleteAll()
        }
    }

    @Test
    fun `create bulk seats`() = runTest {
        val roomId = createAndGetRoomId(
            Room(
                name = "Room 11",
                rows = 5,
                columns = 10
            )
        )
        val seats = mutableListOf<Seat>()

        for (i in 1..10) {
            val seat = Seat(
                roomId = roomId,
                rowLabel = "A",
                seatNumber = i
            )
            seats.add(seat)
        }

        val savedSeats = repo.createSeats(seats)
        assertTrue(savedSeats is DataResult.Success, savedSeats.toString())
        assertTrue(savedSeats.value.isNotEmpty())
        assertEquals(seats.size, savedSeats.value.size)
    }

    @Test
    fun `create bulk seat with non unique seat should fail`() = runTest {
        val roomId = createAndGetRoomId(
            Room(
                name = "Room 12",
                rows = 5, columns = 10
            )
        )

        val result = repo.createSeats(
            listOf(
                Seat(roomId = roomId, rowLabel = "A", seatNumber = 1),
                Seat(roomId = roomId, rowLabel = "A", seatNumber = 1)
            )
        )

        assertTrue(result is DataResult.Failure.UniqueViolation)
    }

    @Test
    fun `get seats by room id should return list of seats`() = runTest {
        val room1Id = createAndGetRoomId(
            Room(
                name = "Room 1",
                rows = 10,
                columns = 5
            )
        )

        val room2Id = createAndGetRoomId(
            Room(name = "Room 2", rows = 5, columns = 10)
        )

        val seats = mutableListOf<Seat>()

        for (i in 1..20) {
            val seat = if (i % 2 == 0) {
                Seat(
                    roomId = room1Id,
                    rowLabel = "A",
                    seatNumber = i
                )
            } else {
                Seat(
                    roomId = room2Id,
                    rowLabel = "B",
                    seatNumber = i
                )
            }
            seats.add(seat)
        }
        repo.createSeats(seats)

        val room1SeatsResult = repo.getSeatsByRoomId(room1Id)
        assertTrue(room1SeatsResult is DataResult.Success)
        assertEquals(10, room1SeatsResult.value.size)
    }

    @Test
    fun `get seats by room id should return empty list`() = runTest {
        val result = repo.getSeatsByRoomId(Random.nextLong())
        assertTrue(result is DataResult.Success, result.toString())
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun `get seats by id should return a seat`() = runTest {
        val roomId = createAndGetRoomId(Room(name = "Room 3", rows = 5, columns = 10))

        val savedSeats = repo.createSeats(
            listOf(
                Seat(
                    roomId = roomId,
                    rowLabel = "A",
                    seatNumber = 1
                ),
                Seat(
                    roomId = roomId,
                    rowLabel = "A",
                    seatNumber = 2
                )
            )
        ) as DataResult.Success

        val seatId = savedSeats.value.first().id

        val retrievedSeatResult = repo.getSeatById(seatId)
        assertTrue(retrievedSeatResult is DataResult.Success)
        assertEquals("A", retrievedSeatResult.value.rowLabel)
    }

    @Test
    fun `get seats by id should return not found`() = runTest {
        val invalidSeatResult = repo.getSeatById(Random.nextLong())
        assertTrue(invalidSeatResult is DataResult.Failure.NotFound)
        assertEquals("Seat not found", invalidSeatResult.errorMessage)
    }

    private suspend fun createAndGetRoomId(room: Room): Long {
        val savedRoomResult = roomRepo.createRoom(
            room
        ) as DataResult.Success
        return savedRoomResult.value.id
    }
}