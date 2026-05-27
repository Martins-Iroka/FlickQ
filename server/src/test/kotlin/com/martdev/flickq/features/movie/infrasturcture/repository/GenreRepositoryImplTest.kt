package com.martdev.flickq.features.movie.infrasturcture.repository

import com.martdev.flickq.features.movie.domain.repository.GenreRepository
import com.martdev.flickq.features.movie.infrasturcture.tables.GenresTable
import com.martdev.flickq.movie.model.Genre
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class GenreRepositoryImplTest {

    companion object {
        private lateinit var genreRepo: GenreRepository

        @Container
        val postgres = PostgresContainer.initPostgres()

        @JvmStatic
        @BeforeAll
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            genreRepo = GenreRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            GenresTable.deleteAll()
        }
    }

    @Test
    fun `save genre successfully`() = runTest {
        val genre = Genre(name = "Action")
        val genre2 = Genre(name = "Adventure")

        val result = genreRepo.saveGenre(genre)
        val result2 = genreRepo.saveGenre(genre2)

        assertTrue(result is DataResult.Success)
        assertEquals(genre.name, result.value.name)

        assertTrue(result2 is DataResult.Success)
        assertEquals(genre2.name, result2.value.name)
    }

    @Test
    fun `save genre with duplicate name should fail`() = runTest {
        genreRepo.saveGenre(Genre(name = "Action"))
        val result = genreRepo.saveGenre(Genre(name = "Action"))
        assertTrue(result is DataResult.Failure.UniqueViolation)
    }

    @Test
    fun `should get saved genres`() = runTest {
        val genre = Genre(name = "Sci-Fi")
        val genre2 = Genre(name = "Animation")

        val result = genreRepo.saveGenre(genre)
        val result2 = genreRepo.saveGenre(genre2)

        assertTrue(result is DataResult.Success)
        assertEquals(genre.name, result.value.name)

        assertTrue(result2 is DataResult.Success)
        assertEquals(genre2.name, result2.value.name)

        val genresResult = genreRepo.getGenres()
        assertTrue(genresResult is DataResult.Success)
        assertEquals(2, genresResult.value.size)
    }

    @Test
    fun `get genres should return empty list`() = runTest {
        val emptyListResult = genreRepo.getGenres()
        assertTrue(emptyListResult is DataResult.Success)
        assertTrue(emptyListResult.value.isEmpty())
    }

    @Test
    fun `should delete genre by id`() = runTest {
        val comedyGenre = Genre(name = "Comedy")
        val dramaGenre = Genre(name = "Drama")

        val result = genreRepo.saveGenre(comedyGenre)
        val result2 = genreRepo.saveGenre(dramaGenre)

        assertTrue(result is DataResult.Success)
        assertTrue(result2 is DataResult.Success)

        val deletedRowResult = genreRepo.deleteGenre(result.value.id)
        assertTrue(deletedRowResult is DataResult.Success)
        assertEquals(1, deletedRowResult.value)

        val genreList = genreRepo.getGenres()
        assertTrue(genreList is DataResult.Success)
        assertEquals(1, genreList.value.size)
    }

    @Test
    fun `should delete genre by invalid id fails`() = runTest {
        val result = genreRepo.deleteGenre(100L)
        assertTrue(result is DataResult.Failure.UnknownError)
        assertEquals("Failed to delete genre with id 100", result.errorMessage)
    }
}