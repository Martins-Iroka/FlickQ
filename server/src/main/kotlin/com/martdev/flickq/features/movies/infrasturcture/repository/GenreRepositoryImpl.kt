package com.martdev.flickq.features.movies.infrasturcture.repository

import com.martdev.flickq.features.movies.domain.repository.GenreRepository
import com.martdev.flickq.features.movies.infrasturcture.tables.GenreEntity
import com.martdev.flickq.features.movies.infrasturcture.tables.GenresTable
import com.martdev.flickq.movies.model.Genre
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.koin.core.annotation.Single

@Single
class GenreRepositoryImpl : GenreRepository {
    override suspend fun saveGenre(genre: Genre): DataResult<Genre> {
        return withSuspendTransaction {
            val entity = GenreEntity.new {
                name = genre.name
            }

            DataResult.Success(
                Genre(id = entity.id.value, name = entity.name)
            )
        }
    }

    override suspend fun getGenres(): DataResult<List<Genre>> {
        return withSuspendTransaction {
            val genres = GenreEntity.all()
                .map {
                    Genre(it.id.value, it.name)
                }

            DataResult.Success(genres)
        }
    }

    override suspend fun deleteGenre(id: Long): DataResult<Int> {
        return withSuspendTransaction {
            val deletedGenreId = GenresTable.deleteWhere {
                GenresTable.id eq id
            }
            if (deletedGenreId == 0) {
                DataResult.Failure.UnknownError("Failed to delete genre with id $id")
            } else DataResult.Success(deletedGenreId)
        }
    }
}