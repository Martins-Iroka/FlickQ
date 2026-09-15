package com.martdev.flickq.feature.movie.presentation.list

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.movie.domain.MovieRepository
import com.martdev.flickq.feature.movie.presentation.MovieUi
import com.martdev.flickq.feature.movie.presentation.toMovieUi
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate

class MovieListPagingSource(
    private val movieRepository: MovieRepository,
    private val date: LocalDate
) : PagingSource<Long, MovieUi>() {
    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, MovieUi> {
        val currentPage = params.key ?: 0
        return try {
            when(val result = movieRepository.getScheduledMovies(date, 6, currentPage)) {
                is Result.Error -> LoadResult.Error(Exception(result.message))
                is Result.Success -> {
                    val movies = result.data.movies.map {
                        it.toMovieUi()
                    }
                    val next = result.data.nextOffset.takeIf { it >= 0 }
                    LoadResult.Page(
                        data = movies,
                        prevKey = null,
                        nextKey = next
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, MovieUi>): Long? {
        return state.anchorPosition?.let {
            val anchorPage = state.closestPageToPosition(it)
            anchorPage?.prevKey?.plus(1)
        }
    }

}