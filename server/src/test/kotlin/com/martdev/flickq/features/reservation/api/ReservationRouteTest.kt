package com.martdev.flickq.features.reservation.api

import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.reservation.domain.service.ReservationCancellationService
import com.martdev.flickq.features.reservation.domain.service.ReservationService
import com.martdev.flickq.features.reservation.domain.service.ShowtimeSeatService
import com.martdev.flickq.reservation.CreateReservationRequest
import com.martdev.flickq.reservation.model.Reservation
import com.martdev.flickq.reservation.model.ShowtimeSeat
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
class ReservationRouteTest {

    @MockK
    private lateinit var reservationService: ReservationService

    @MockK
    private lateinit var showtimeSeatService: ShowtimeSeatService

    @MockK
    private lateinit var cancellationService: ReservationCancellationService

    private val jwtConfig = JWTConfig("test", 15, "iss", "aud")

    private val reservationModule = module {
        single { reservationService }
        single { showtimeSeatService }
        single { cancellationService }
        single { jwtConfig }
    }

    private val jwt = JWTAuthImpl(jwtConfig)
    private val adminToken = jwt.generateAccessToken("1", "ADMIN")
    private val userToken = jwt.generateAccessToken("2", "USER")

    // -- Admin routes --

    @Test
    fun testPostAdminReservationPopulateseatsShowtimeid() = testApplication {
        coJustRun { showtimeSeatService.populateShowtimeSeats(any()) }

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.post("/admin/reservation/populate-seats/1").apply {
            assertEquals(HttpStatusCode.Created, status, bodyAsText())
        }
    }

    @Test
    fun testGetAdminReservationGetall() = testApplication {
        coEvery {
            reservationService.getAllReservations(any(), any())
        } returns listOf(Reservation())

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reservation/get-all").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun testGetAdminReservationGetbyidReservationid() = testApplication {
        coEvery {
            reservationService.getReservationById(any())
        } returns Reservation()

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.get("/admin/reservation/get-by-id/1").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun testPatchAdminReservationCancelReservationid() = testApplication {
        coEvery {
            cancellationService.cancelByAdmin(any())
        } returns Reservation()

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.patch("/admin/reservation/cancel/1").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun testAdminReservationRejectsNonAdminCaller() = testApplication {
        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.get("/admin/reservation/get-all").apply {
            assertEquals(HttpStatusCode.Forbidden, status, bodyAsText())
        }
    }

    @Test
    fun testPostReservationCreate() = testApplication {
        coEvery {
            reservationService.createReservation(any(), any(), any())
        } returns Reservation()

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.post("/reservation/create") {
            setBody(CreateReservationRequest(showtimeId = 1, seatIds = listOf(1, 2, 3)))
        }.apply {
            assertEquals(HttpStatusCode.Created, status, bodyAsText())
        }
    }

    @Test
    fun testGetReservationMyreservations() = testApplication {
        coEvery {
            reservationService.getMyReservations(any())
        } returns listOf(Reservation())

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.get("/reservation/my-reservations").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun testGetReservationReservationid() = testApplication {
        coEvery {
            reservationService.getMyReservationById(any(), any())
        } returns Reservation()

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.get("/reservation/1").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun testGetReservationAvailableseatsShowtimeid() = testApplication {
        coEvery {
            showtimeSeatService.getAvailableSeats(any())
        } returns listOf(ShowtimeSeat())

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.get("/reservation/available-seats/1").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun testPatchReservationCancelReservationid() = testApplication {
        coEvery {
            reservationService.cancelReservation(any(), any())
        } returns Reservation()

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.patch("/reservation/cancel/1").apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun testUserReservationRejectsUnauthenticatedCaller() = testApplication {
        application {
            appConfig()
        }
        val client = clientConfiguration()
        client.get("/reservation/my-reservations").apply {
            assertEquals(HttpStatusCode.Unauthorized, status, bodyAsText())
        }
    }

    private fun Application.appConfig() = testAppConfiguration(reservationModule) {
        reservationRoute()
    }
}
