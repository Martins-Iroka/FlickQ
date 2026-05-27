package com.martdev.flickq.features.room.api

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.room.domain.service.SeatService
import com.martdev.flickq.room.SeatDTO
import com.martdev.flickq.room.model.Seat
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class SeatRouteTest {

    @MockK
    private lateinit var service: SeatService

    private val jwtConfig = JWTConfig("test", 15, "iss", "aud")

    private val module = module {
        single { service }
        single { jwtConfig }
    }

    private val adminToken = JWTAuthImpl(jwtConfig).generateAccessToken("1", Role.ADMIN.name)

    @Test
    fun testPost_AdminSeat_CreateSeats() = testApplication {
        coEvery {
            service.createSeats(any())
        } returns listOf(Seat())

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)

        client.post("/admin/seat/create-seats") {
            setBody(listOf(SeatDTO()))
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }
    }

    @Test
    fun testGetSeat_GetSeatById() = testApplication {
        coEvery {
            service.getSeatById(any())
        } returns Seat()

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.get("/seat/get-seat-by-id/1").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testGet_GetSeatsByRoomId() = testApplication {
        coEvery {
            service.getSeatsByRoomId(any())
        } returns listOf(Seat())

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)

        client.get("/seat/get-seats-by-room-id/1").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    private fun Application.appConfig() = testAppConfiguration(module) {
        seatRoute()
    }
}