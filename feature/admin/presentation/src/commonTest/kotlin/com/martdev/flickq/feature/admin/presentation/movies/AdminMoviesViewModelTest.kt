package com.martdev.flickq.feature.admin.presentation.movies

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.martdev.flickq.feature.admin.presentation.FakeAdminCatalogRepository
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesAction
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesViewModel
import com.martdev.flickq.movie.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminMoviesViewModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun movies(count: Int) = (1..count).map { Movie(id = it.toLong(), title = "Movie $it") }

    @Test
    fun `paginates admin movies with load-more until exhausted`() = runTest {
        val repo = FakeAdminCatalogRepository().apply { allMovies = movies(120) }
        val vm = AdminMoviesViewModel(repo)

        assertThat(vm.state.value.movies).hasSize(50)
        assertThat(vm.state.value.endReached).isFalse()

        vm.onAction(AdminMoviesAction.OnLoadMore)
        assertThat(vm.state.value.movies).hasSize(100)
        assertThat(vm.state.value.endReached).isFalse()

        vm.onAction(AdminMoviesAction.OnLoadMore)
        assertThat(vm.state.value.movies).hasSize(120)
        assertThat(vm.state.value.endReached).isTrue()
        assertThat(vm.state.value.canLoadMore).isFalse()

        // Exhausted: a further load-more fetches nothing more.
        vm.onAction(AdminMoviesAction.OnLoadMore)
        assertThat(repo.moviePages).isEqualTo(listOf(50 to 0, 50 to 50, 50 to 100))
    }

    @Test
    fun `a short first page marks the end with no load-more`() = runTest {
        val repo = FakeAdminCatalogRepository().apply { allMovies = movies(3) }
        val vm = AdminMoviesViewModel(repo)

        assertThat(vm.state.value.movies).hasSize(3)
        assertThat(vm.state.value.endReached).isTrue()
        assertThat(vm.state.value.canLoadMore).isFalse()
    }
}
