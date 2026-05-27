package com.martdev.flickq.features.movies.infrasturcture.repository

import com.martdev.flickq.features.movies.domain.repository.GenreRepository
import com.martdev.flickq.features.movies.domain.repository.MovieRepository
import com.martdev.flickq.features.movies.infrasturcture.tables.GenresTable
import com.martdev.flickq.features.movies.infrasturcture.tables.MovieGenreTable
import com.martdev.flickq.features.movies.infrasturcture.tables.MoviesTable
import com.martdev.flickq.movies.model.Genre
import com.martdev.flickq.movies.model.Movie
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.utils.PostgresContainer
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
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
class MovieRepositoryImplTest {

    companion object {
        private lateinit var movieRepo: MovieRepository
        private lateinit var genreRepo: GenreRepository
        @Container
        val postgres = PostgresContainer.initPostgres()

        @JvmStatic
        @BeforeAll
        fun connectToDBAndMigrate() {
            PostgresContainer.connectToDBAndMigrate(postgres)
            genreRepo = GenreRepositoryImpl()
            movieRepo = MovieRepositoryImpl()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            // Order is important due to foreign key constraints
            MovieGenreTable.deleteAll()
            MoviesTable.deleteAll()
            GenresTable.deleteAll()
        }
    }

    @Test
    fun `saveMovieData should successfully save a movie and its genres`() = runTest {
        // Arrange
        val genres = createAndSaveGenres("Action", "Adventure")
        val movie = createMovie(title = "Inception", genres = genres)

        // Act
        val result = movieRepo.createMovie(movie)

        // Assert
        assertTrue(result is DataResult.Success, "Movie should be saved successfully")
        val savedMovieId = result.value

        val fetchedResult = movieRepo.getMovieById(savedMovieId)
        assertTrue(fetchedResult is DataResult.Success, "Saved movie should be fetchable")
        assertEquals("Inception", fetchedResult.value.title)
        assertEquals(2, fetchedResult.value.genres.size)
    }

    @Test
    fun `saveMovieData should fail to save a movie and its genres returns not found for invalid genre`() = runTest {
        // Arrange
        val genres = listOf(Genre())
        val movie = createMovie(title = "Apex", genres = genres)

        // Act
        val result = movieRepo.createMovie(movie)

        // Assert
        assertTrue(result is DataResult.Failure.NotFound, "Movie should be not be saved")

        val movies = movieRepo.getMovies(limit = 10, offset = 0) as DataResult.Success
        assertEquals(0, movies.value.size, "Failed save should not persist the movie")
    }

    @Test
    fun `getMovies should return a paginated list of movies`() = runTest {
        // Arrange
        val genres = createAndSaveGenres("Sci-Fi")
        createAndSaveMovie(title = "Movie 1", genres = genres)
        createAndSaveMovie(title = "Movie 2", genres = genres)
        createAndSaveMovie(title = "Movie 3", genres = genres)

        // Act: Get the first page with 2 items
        val page1Result = movieRepo.getMovies(limit = 2, offset = 0)

        // Assert: Page 1
        assertTrue(page1Result is DataResult.Success)
        assertEquals(2, page1Result.value.size)

        // Act: Get the second page with 2 items (should only have 1 left)
        val page2Result = movieRepo.getMovies(limit = 2, offset = 2)

        // Assert: Page 2
        assertTrue(page2Result is DataResult.Success)
        assertEquals(1, page2Result.value.size)
    }

    @Test
    fun `getMovies should return empty list of movies`() = runTest {
        val emptyListResult = movieRepo.getMovies(2, 0)

        assertTrue(emptyListResult is DataResult.Success)
        assertTrue(emptyListResult.value.isEmpty())
    }

    @Test
    fun `getMovieById should return a specific movie when ID exists`() = runTest {
        // Arrange
        val genres = createAndSaveGenres("Drama")
        val savedMovieId = createAndSaveMovie(title = "The Godfather", genres = genres)

        // Act
        val result = movieRepo.getMovieById(savedMovieId)

        // Assert
        assertTrue(result is DataResult.Success)
        assertEquals("The Godfather", result.value.title)
    }

    @Test
    fun `getMovieById should return NotFound when ID does not exist`() = runTest {
        // Arrange: DB is empty thanks to @BeforeEach

        // Act
        val result = movieRepo.getMovieById(999L)

        // Assert
        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `updateMovie should correctly change movie data`() = runTest {
        // Arrange
        val genres = createAndSaveGenres("Action")
        val originalMovieId = createAndSaveMovie(title = "Old Title", genres = genres)
        val movieToUpdate = Movie(
            id = originalMovieId,
            title = "New Title",
            description = "Updated Description",
            posterUrl = "new_poster.jpg",
            duration = 150,
            releasedDate = LocalDate(2025, Month.JANUARY, 1),
            genres = genres
        )

        // Act
        val updateResult = movieRepo.updateMovie(movieToUpdate)

        // Assert
        assertTrue(updateResult is DataResult.Success)
        assertEquals(originalMovieId, updateResult.value.id)

        val fetchedResult = movieRepo.getMovieById(originalMovieId)
        assertTrue(fetchedResult is DataResult.Success)
        assertEquals("New Title", fetchedResult.value.title)
        assertEquals("Updated Description", fetchedResult.value.description)
    }

    @Test
    fun `updateMovie should fail due to invalid movie data`() = runTest {
        // Arrange
        val invalidMovieData = Movie(
            id = 77,
        )

        // Act
        val updateResult = movieRepo.updateMovie(invalidMovieData)

        // Assert
        assertTrue(updateResult is DataResult.Failure.NotFound)
    }

    @Test
    fun `getMoviesByGenre should return only movies with the specified genre`() = runTest {
        // Arrange
        val actionGenre = (genreRepo.saveGenre(Genre(name = "Action")) as DataResult.Success).value
        val dramaGenre = (genreRepo.saveGenre(Genre(name = "Drama")) as DataResult.Success).value

        createAndSaveMovie(title = "Die Hard", genres = listOf(actionGenre))
        createAndSaveMovie(title = "The Dark Knight", genres = listOf(actionGenre, dramaGenre))
        createAndSaveMovie(title = "Forrest Gump", genres = listOf(dramaGenre))

        // Act
        val result = movieRepo.getMoviesByGenre(actionGenre.id, limit = 5, offset = 0)

        // Assert
        assertTrue(result is DataResult.Success)
        assertEquals(2, result.value.size)
        assertTrue(result.value.none { it.title == "Forrest Gump" })
    }

    @Test
    fun `getMoviesByGenre should fail to return movies with the specified genre`() = runTest {
        // Arrange
        val invalidGenreId = 99L
        // Act
        val result = movieRepo.getMoviesByGenre(invalidGenreId, limit = 5, offset = 0)

        // Assert
        assertTrue(result is DataResult.Failure.NotFound)
    }

    @Test
    fun `deleteMovie should remove a movie from the database`() = runTest {
        // Arrange
        val movieId = createAndSaveMovie(title = "To Be Deleted", genres = createAndSaveGenres("Temporary"))

        // Act
        val deleteResult = movieRepo.deleteMovie(movieId)

        // Assert
        assertTrue(deleteResult is DataResult.Success)
        assertEquals(1, deleteResult.value, "Delete should report 1 row affected")

        val fetchResult = movieRepo.getMovieById(movieId)
        assertTrue(fetchResult is DataResult.Failure.NotFound, "Fetching a deleted movie should result in NotFound")
    }

    @Test
    fun `deleteMovie should return UnknownError when trying to delete a non-existent movie`() = runTest {
        // Arrange: DB is empty
        val nonExistentId = 999L

        // Act
        val result = movieRepo.deleteMovie(nonExistentId)

        // Assert
        assertTrue(result is DataResult.Failure.UnknownError)
        assertEquals("Failed to delete movie", result.errorMessage)
    }

    private suspend fun createAndSaveGenres(vararg names: String): List<Genre> {
        return names.map { name ->
            (genreRepo.saveGenre(Genre(name = name)) as DataResult.Success).value
        }
    }

    private suspend fun createAndSaveMovie(title: String, genres: List<Genre>): Long {
        val movie = createMovie(title = title, genres = genres)
        return (movieRepo.createMovie(movie) as DataResult.Success).value
    }

    private fun createMovie(title: String, genres: List<Genre>) = Movie(
        title = title,
        description = "A movie description",
        posterUrl = "https://example.com/poster.jpg",
        duration = 120,
        releasedDate = LocalDate(2026, Month.MAY, 5),
        genres = genres
    )
}