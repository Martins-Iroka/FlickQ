package com.martdev.flickq.features.movie.api

import com.martdev.flickq.movie.GenreDTO
import com.martdev.flickq.movie.MovieDTO
import com.martdev.flickq.movie.MovieListItemDTO
import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import kotlinx.datetime.LocalDate

fun Movie.toMovieItemDto() = MovieListItemDTO(
    id, title, posterUrl
)

fun Movie.toMovieDto() = MovieDTO(
    id,
    title,
    description,
    posterUrl,
    duration,
    releasedDate = releasedDate.toString(),
    genres.map { it.toGenreDto() }
)

val formatter = LocalDate.Formats.ISO
fun MovieDTO.toMovie() = Movie(
    title = title,
    description = description,
    posterUrl = posterUrl,
    duration = duration,
    releasedDate = formatter.parse(releasedDate),
    genres = genres.map { Genre(name = it.name) }
)

fun Genre.toGenreDto() = GenreDTO(
    id, name
)

fun GenreDTO.toGenre() = Genre(
    name = name
)