package com.martdev.flickq.features.movie.infrasturcture.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object MovieGenreTable : Table("movie_genre") {
    val movieId = reference(
        "movie_id",
        MoviesTable,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )
    val genreId = reference(
        "genre_id",
        GenresTable,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    )

    override val primaryKey: PrimaryKey = PrimaryKey(
        movieId, genreId
    )
}