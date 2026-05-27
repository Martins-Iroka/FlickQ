package com.martdev.flickq.features.movies.infrasturcture.tables

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

object GenresTable : LongIdTable("genres") {
    val name = varchar("name", 50)
}

class GenreEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<GenreEntity>(GenresTable)

    var name by GenresTable.name
    val movies by MoviesEntity via MovieGenreTable
}