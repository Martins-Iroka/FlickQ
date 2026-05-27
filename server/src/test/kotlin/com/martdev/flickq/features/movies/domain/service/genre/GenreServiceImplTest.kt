package com.martdev.flickq.features.movies.domain.service.genre

import com.martdev.flickq.features.movies.domain.repository.GenreRepository
import com.martdev.flickq.movies.model.Genre
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
class GenreServiceImplTest {

    @MockK
    private lateinit var repository: GenreRepository

    private lateinit var service: GenreService

    @BeforeEach
    fun setUp() {
        service = GenreServiceImpl(repository)
    }

    @Test
    fun `should create genre successfully`() = runTest {
        coEvery {
            repository.saveGenre(any())
        } returns DataResult.Success(Genre(id = 1))

        service.createGenre(Genre())

        coVerify {
            repository.saveGenre(any())
        }
    }

    @Test
    fun `should get list of genres successfully`() = runTest {
        coEvery {
            repository.getGenres()
        } returns DataResult.Success(listOf(Genre()))

        val result = service.getGenres()

        assertTrue(result.isNotEmpty())
        assertEquals(1, result.size)
    }

    @Test
    fun `should delete genre successfully`() = runTest {
        coEvery {
            repository.deleteGenre(any())
        } returns DataResult.Success(1)

        service.deleteGenre(1)

        coVerify {
            repository.deleteGenre(any())
        }
    }
}