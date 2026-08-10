package com.martdev.flickq.feature.movie.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.movie.domain.MovieRepository
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import kotlinx.datetime.LocalDate

/**
 * In-memory movie catalog used while the app runs on fakes. Swapped for a
 * Ktor-backed implementation (mapping MovieDTO -> Movie) when wiring the real API.
 */
class FakeMovieDataSource : MovieRepository {

    private val catalog: List<Movie> = listOf(
        Movie(
            id = 1,
            title = "Neon Skyline",
            description = "A burnt-out courier discovers a conspiracy threaded through the " +
                "city's automated traffic grid and has one night to expose it.",
            posterUrl = "https://images.flickq.dev/posters/neon-skyline.jpg",
            duration = 128,
            releasedDate = LocalDate(2025, 11, 14),
            genres = listOf(Genre(1, "Sci-Fi"), Genre(2, "Thriller"))
        ),
        Movie(
            id = 2,
            title = "The Quiet Coast",
            description = "Two estranged sisters reunite to restore their late mother's " +
                "seaside inn across one transformative summer.",
            posterUrl = "https://images.flickq.dev/posters/quiet-coast.jpg",
            duration = 112,
            releasedDate = LocalDate(2025, 7, 3),
            genres = listOf(Genre(3, "Drama"))
        ),
        Movie(
            id = 3,
            title = "Paper Lanterns",
            description = "An animated coming-of-age tale following a girl who can fold " +
                "memories into origami that come briefly to life.",
            posterUrl = "https://images.flickq.dev/posters/paper-lanterns.jpg",
            duration = 99,
            releasedDate = LocalDate(2026, 1, 23),
            genres = listOf(Genre(4, "Animation"), Genre(5, "Family"))
        ),
        Movie(
            id = 4,
            title = "Last Call at the Atlas",
            description = "A heist crew targets a vault beneath a legendary hotel on the " +
                "night before its demolition.",
            posterUrl = "https://images.flickq.dev/posters/last-call-atlas.jpg",
            duration = 141,
            releasedDate = LocalDate(2025, 9, 27),
            genres = listOf(Genre(2, "Thriller"), Genre(6, "Crime"))
        ),
        Movie(
            id = 5,
            title = "Comet Season",
            description = "A small mountain town becomes the unlikely epicenter of a global " +
                "event when a comet lingers in its sky for a week.",
            posterUrl = "https://images.flickq.dev/posters/comet-season.jpg",
            duration = 134,
            releasedDate = LocalDate(2026, 3, 6),
            genres = listOf(Genre(1, "Sci-Fi"), Genre(3, "Drama"))
        ),
        Movie(
            id = 6,
            title = "Double Espresso",
            description = "A workaholic barista and a clumsy regular keep meeting by " +
                "accident across the city in this warm romantic comedy.",
            posterUrl = "https://images.flickq.dev/posters/double-espresso.jpg",
            duration = 104,
            releasedDate = LocalDate(2025, 12, 19),
            genres = listOf(Genre(7, "Romance"), Genre(8, "Comedy"))
        )
    )

    override suspend fun getMovies(
        limit: Int,
        offset: Int,
        date: LocalDate?
    ): Result<List<Movie>, DataError> =
        Result.Success(catalog.drop(offset).take(limit))

    override suspend fun getMovieById(id: Long): Result<Movie, DataError> =
        catalog.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)
}
