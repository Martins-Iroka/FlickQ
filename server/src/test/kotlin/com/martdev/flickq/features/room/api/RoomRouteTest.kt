package com.martdev.flickq.features.room.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.room.domain.service.RoomService
import com.martdev.flickq.room.RoomDTO
import com.martdev.flickq.room.model.Room
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class RoomRouteTest {

    @MockK
    private lateinit var service: RoomService

    private val jwtConfig = JWTConfig(secret = "test", exp = 15, issuer = "iss", audience = "aud")

    val module = module {
        single { service }
        single { jwtConfig }
    }

    val adminToken = JWTAuthImpl(jwtConfig).generateAccessToken("1", Role.ADMIN.name)
    val userToken = JWTAuthImpl(jwtConfig).generateAccessToken("1", Role.USER.name)

    @Test
    fun testPost_AdminRoom_CreateRoom() = testApplication {
        coEvery {
            service.createRoom(any())
        } returns Room()
        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.post("/admin/room/create-room") {
            setBody(
                RoomDTO(
                    name = "Room 1",
                    rows = 5,
                    columns = 10
                )
            )
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }
    }

    @Test
    fun testDelete_AdminRoom_DeleteRoomRoomId() = testApplication {
        coJustRun {
            service.deleteRoom(any())
        }

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.delete("/admin/room/delete-room/1").apply {
            assertEquals(HttpStatusCode.NoContent, status)
        }
    }

    @Test
    fun testPut_AdminRoom_UpdateRoomByRoomId() = testApplication {
        coEvery {
            service.updateRoom(any())
        } returns Room()

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)

        client.put("/admin/room/update-room/1") {
            setBody(RoomDTO(1, "Room 1", rows = 5, columns = 10))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testGetRoom_GetRoomById() = testApplication {
        coEvery {
            service.getRoomById(any())
        } returns Room()

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.get("/room/get-room-by-id/1").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testGetRooms() = testApplication {
        coEvery {
            service.getAllRooms()
        } returns listOf(Room())

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)

        client.get("/room/get-rooms").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    private fun Application.appConfig() = testAppConfiguration(module) {
        roomRoute()
    }
}