package com.martdev.flickq.features.movies.api

import com.martdev.flickq.config.JWTConfig
import com.martdev.flickq.features.auth.infrastructure.security.JWTAuthImpl
import com.martdev.flickq.features.movies.api.movie.movieRoute
import com.martdev.flickq.features.movies.domain.service.movie.MovieService
import com.martdev.flickq.movies.GenreDTO
import com.martdev.flickq.movies.MovieDTO
import com.martdev.flickq.movies.model.Movie
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
class MovieRouteTest {

    @MockK
    private lateinit var service: MovieService

    private val jwtConfig = JWTConfig(
        "test-secret", 15, "iss", "audience"
    )

    private val movieTestModule = module {
        single { service }
        single { jwtConfig }
    }

    private val adminToken = JWTAuthImpl(jwtConfig).generateAccessToken("1", "ADMIN")
    private val userToken = JWTAuthImpl(jwtConfig).generateAccessToken("2", "USER")

    private val movie = MovieDTO(
        title = "title",
        description = "description",
        posterUrl = "poster@example.com",
        duration = 90,
        releasedDate = "2026-05-11",
        genres = listOf(
            GenreDTO(name = "Action")
        )
    )

    @Test
    fun testPostAdminMovieCreateMovie() = testApplication {
        coJustRun {
            service.createMovie(any())
        }
        application {
            configure()
        }
        val client = clientConfiguration(adminToken)

        client.post("/admin/movie/create-movie") {
            setBody(movie)
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }
    }

    @Test
    fun testPostAdminMovieCreateMovie_returnsForbidden() = testApplication {
        coJustRun {
            service.createMovie(any())
        }
        application {
            configure()
        }
        val client = clientConfiguration(userToken)

        client.post("/admin/movie/create-movie") {
            setBody(movie)
        }.apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    @Test
    fun testDeleteAdminMovieById() = testApplication {
        coJustRun {
            service.deleteMovie(any())
        }
        application {
            configure()
        }
        val client = clientConfiguration(adminToken)
        client.delete("/admin/movie/delete-movie/1").apply {
            assertEquals(HttpStatusCode.NoContent, status)
        }
    }

    @Test
    fun testPutAdminMovieUpdateMovie() = testApplication {
        coEvery {
            service.updateMovie(any())
        } returns Movie()

        application {
            configure()
        }
        val client = clientConfiguration(adminToken)
        client.put("/admin/movie/update-movie/1") {
            setBody(movie)
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testGetMovieById() = testApplication {
        coEvery {
            service.getMovieById(any())
        } returns Movie()

        application {
            configure()
        }
        val client = clientConfiguration(userToken)
        client.get("movie/get-movie-by-id/5").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testGetMovies() = testApplication {
        coEvery {
            service.getMovies(any(), any())
        } returns listOf(Movie())

        application {
            configure()
        }
        val client = clientConfiguration(userToken)
        client.get("movie/get-movies").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testGetMoviesByGenreId() = testApplication {
        coEvery {
            service.getMoviesByGenre(any(), any(), any())
        } returns listOf(Movie())

        application {
            configure()
        }
        val client = clientConfiguration(userToken)
        client.get("movie/get-movies-by-genre/15").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    private fun Application.configure() = testAppConfiguration(movieTestModule) { movieRoute() }
}