package com.martdev.flickq.features.movie.api

import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.movie.api.genre.genreRoute
import com.martdev.flickq.features.movie.domain.service.genre.GenreService
import com.martdev.flickq.movie.GenreDTO
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.utils.clientConfiguration
import com.martdev.flickq.utils.testAppConfiguration
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
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
class GenreRouteTest {

    @MockK
    private lateinit var service: GenreService

    val jwtConfig = JWTConfig("test", 15, "iss", "audience")

    val module = module {
        single { service }
        single { jwtConfig }
    }

    val adminToken = JWTAuthImpl(jwtConfig).generateAccessToken("5", "ADMIN")
    val userToken = JWTAuthImpl(jwtConfig).generateAccessToken("7", "USER")

    @Test
    fun testPostAdminGenre() = testApplication {
        coJustRun {
            service.createGenre(any())
        }
        application {
            configure()
        }
        val client = clientConfiguration(adminToken)
        client.post("/admin/genre/create-genre") {
            setBody(GenreDTO(name = "Action"))
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }
    }

    @Test
    fun testDeleteAdminGenreById() = testApplication {
        coJustRun {
            service.deleteGenre(any())
        }
        application {
            configure()
        }
        val client = clientConfiguration(adminToken)
        client.delete("/admin/genre/delete-genre/50").apply {
            assertEquals(HttpStatusCode.NoContent, status)
        }
    }

    @Test
    fun testGetGenres() = testApplication {
        coEvery {
            service.getGenres()
        } returns listOf(Genre())

        application {
            configure()
        }
        val client = clientConfiguration(userToken)
        client.get("/genre/genres").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    private fun Application.configure() = testAppConfiguration(module) {
        genreRoute()
    }
}