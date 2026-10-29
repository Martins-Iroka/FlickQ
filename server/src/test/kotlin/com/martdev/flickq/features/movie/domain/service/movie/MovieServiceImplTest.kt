package com.martdev.flickq.features.movie.domain.service.movie

import com.martdev.flickq.features.movie.domain.repository.MovieRepository
import com.martdev.flickq.movie.model.Movie
import com.martdev.flickq.shared.domain.exception.InternalServerException
import com.martdev.flickq.shared.domain.exception.NotFoundException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class MovieServiceImplTest {

    @MockK
    private lateinit var repository: MovieRepository

    private lateinit var service: MovieService

    @BeforeEach
    fun setup() {
        service = MovieServiceImpl(repository)
    }

    @Test
    fun `should create movie successfully`() = runTest {
        coEvery {
            repository.createMovie(any())
        } returns DataResult.Success(0L)

        service.createMovie(
            Movie()
        )
        coVerify {
            repository.createMovie(any())
        }
    }

    @Test
    fun `create movie should throw not found exception`() = runTest {
        coEvery {
            repository.createMovie(any())
        } returns DataResult.Failure.NotFound()

        assertFailsWith<NotFoundException> {
            service.createMovie(Movie())
        }
    }

    @Test
    fun `should get list of movies successfully`() = runTest {
        coEvery {
            repository.getMovies(any(), any())
        } returns DataResult.Success(listOf(Movie()))

        val result = service.getMovies(5, 0)
        assertTrue(result.isNotEmpty())
        assertEquals(1, result.size)
    }

    @Test
    fun `should get list of movies should return empty list`() = runTest {
        coEvery {
            repository.getMovies(any(), any())
        } returns DataResult.Success(emptyList())

        val result = service.getMovies(5, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should update movie successfully`() = runTest {
        coEvery {
            repository.updateMovie(any())
        } returns DataResult.Success(Movie(id = 1))

        val result = service.updateMovie(Movie())
        assertEquals(1, result.id)
    }

    @Test
    fun `update movie should throw not found exception`() = runTest {
        coEvery {
            repository.updateMovie(any())
        } returns DataResult.Failure.NotFound()

        assertFailsWith<NotFoundException> {
            service.updateMovie(Movie())
        }
    }

    @Test
    fun `should delete movie successfully`() = runTest {
        coEvery {
            repository.deleteMovie(any())
        } returns DataResult.Success(1)

        service.deleteMovie(1)

        coVerify {
            repository.deleteMovie(any())
        }
    }

    @Test
    fun `delete movie throws internal server exception`() = runTest {
        coEvery {
            repository.deleteMovie(any())
        } returns DataResult.Failure.UnknownError("error")

        assertFailsWith<InternalServerException> {
            service.deleteMovie(1)
        }
    }

    @Test
    fun `should get movies by genre successfully`() = runTest {
        coEvery {
            repository.getMoviesByGenre(
                any(), any(), any()
            )
        } returns DataResult.Success(listOf(Movie()))

        val result = service.getMoviesByGenre(1, 1, 1)
        assertTrue(result.isNotEmpty())
        assertEquals(1, result.size)
    }

    @Test
    fun `should get movies by genre returns empty list`() = runTest {
        coEvery {
            repository.getMoviesByGenre(any(), any(), any())
        } returns DataResult.Success(emptyList())

        val result = service.getMoviesByGenre(1, 1, 1)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should get movies by genre throws not found exception`() = runTest {
        coEvery {
            repository.getMoviesByGenre(any(), any(), any())
        } returns DataResult.Failure.NotFound()

        assertFailsWith<NotFoundException> {
            service.getMoviesByGenre(1, 1, 1)
        }
    }
}