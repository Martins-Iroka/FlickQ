package com.martdev.flickq.features.movies.domain.service.genre

import com.martdev.flickq.features.movies.domain.repository.GenreRepository
import com.martdev.flickq.movies.model.Genre
import com.martdev.flickq.shared.util.returnValue
import org.koin.core.annotation.Single

@Single
class GenreServiceImpl(
    private val repository: GenreRepository
) : GenreService {
    override suspend fun createGenre(genre: Genre) {
        repository.saveGenre(genre).returnValue()
    }

    override suspend fun getGenres(): List<Genre> {
        return repository.getGenres().returnValue()
    }

    override suspend fun deleteGenre(id: Long) {
        repository.deleteGenre(id).returnValue()
    }
}