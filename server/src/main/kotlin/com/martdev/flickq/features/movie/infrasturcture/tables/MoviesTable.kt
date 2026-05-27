package com.martdev.flickq.features.movie.infrasturcture.tables

import com.martdev.flickq.movie.model.Genre
import com.martdev.flickq.movie.model.Movie
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object MoviesTable : LongIdTable("movies") {
    val title = varchar("title", 255)
    val description = text("description")
    val posterUrl = text("poster_url")
    val duration = integer("duration")
    val releasedDate = date("released_date")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

class MoviesEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<MoviesEntity>(MoviesTable)

    var title by MoviesTable.title
    var description by MoviesTable.description
    var posterUrl by MoviesTable.posterUrl
    var duration by MoviesTable.duration
    var releasedDate by MoviesTable.releasedDate
    var genres by GenreEntity via MovieGenreTable
}

fun MoviesEntity.toMovie() = Movie(
    id = id.value,
    title = title,
    description = description,
    posterUrl = posterUrl,
    duration = duration,
    releasedDate = releasedDate,
    genres = genres.map {
        Genre(it.id.value, it.name)
    }
)