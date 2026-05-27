package com.martdev.flickq.features.showtime.api

import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.showtime.domain.service.ShowtimeService
import com.martdev.flickq.showtime.ShowtimeDTO
import com.martdev.flickq.showtime.UpdateShowtimeStatusRequest
import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class ShowtimeRouteTest {

    @MockK
    private lateinit var service: ShowtimeService

    private val jwtConfig = JWTConfig("test", 15, "iss", "aud")

    private val showtimeModule = module {
        single { service }
        single { jwtConfig }
    }
    private val jwt = JWTAuthImpl(jwtConfig)
    private val adminToken = jwt.generateAccessToken("1", "ADMIN")
    private val userToken = jwt.generateAccessToken("2", "USER")

    private val showtimeDTO = ShowtimeDTO(
        movieId = 1,
        roomId = 2,
        startsAt = Instant.parseOrNull("2026-05-18T16:30:00Z"),
        endsAt = Instant.parseOrNull("2026-05-18T18:30:00Z"),
        price = 5000,
        status = "SCHEDULED"
    )

    @Test
    fun testPostAdminShowtimeCreateshowtime() = testApplication {
        coEvery {
            service.createShowtime(any())
        } returns Showtime()

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.post("/admin/showtime/create-showtime") {
            setBody(showtimeDTO)
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }
    }

    @Test
    fun testDeleteAdminShowtimeDeleteshowtimeShowtimeid() = testApplication {
        coJustRun {
            service.deleteShowtime(any())
        }

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)

        client.delete("/admin/showtime/delete-showtime/1").apply {
            assertEquals(HttpStatusCode.NoContent, status)
        }
    }

    @Test
    fun testGetAdminShowtimeGetshowtimes() = testApplication {
        coEvery {
            service.getShowtimes(any(), any())
        } returns listOf(Showtime())

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.get("/admin/showtime/get-showtimes").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testPatchAdminShowtimeUpdateshowtimestatusShowtimeid() = testApplication {
        coEvery {
            service.updateShowtimeStatus(any(), any())
        } returns Showtime()

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.patch("/admin/showtime/update-showtime-status/1") {
            setBody(UpdateShowtimeStatusRequest("SCHEDULED"))
        }.apply {
            assertEquals(HttpStatusCode.OK, status, bodyAsText())
        }
    }

    @Test
    fun testPutAdminShowtimeUpdateshowtimeShowtimeid() = testApplication {
        coEvery {
            service.updateShowtime(any())
        } returns Showtime()

        application {
            appConfig()
        }
        val client = clientConfiguration(adminToken)
        client.put("/admin/showtime/update-showtime/1") {
            setBody(showtimeDTO)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testGetShowtimeGetshowtimebyidShowtimeid() = testApplication {
        coEvery {
            service.getShowtimeById(any())
        } returns Showtime()

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.get("/showtime/get-showtime-by-id/1").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testGetShowtimeGetshowtimesbymovieidMovieid() = testApplication {
        coEvery {
            service.getShowtimesByMovieId(any())
        } returns listOf(Showtime())

        application {
            appConfig()
        }
        val client = clientConfiguration(userToken)
        client.get("/showtime/get-showtimes-by-movie-id/11").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    private fun Application.appConfig() = testAppConfiguration(showtimeModule) {
        showtimeRoute()
    }
}