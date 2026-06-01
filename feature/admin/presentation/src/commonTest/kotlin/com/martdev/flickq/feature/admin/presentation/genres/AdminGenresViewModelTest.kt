package com.martdev.flickq.feature.admin.presentation.genres

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.feature.admin.presentation.FakeAdminCatalogRepository
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
class AdminGenresViewModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loads genres on init`() = runTest {
        val vm = AdminGenresViewModel(FakeAdminCatalogRepository())
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.genres).hasSize(1)
    }

    @Test
    fun `creating a genre saves, closes the dialog and reloads`() = runTest {
        val repo = FakeAdminCatalogRepository()
        val vm = AdminGenresViewModel(repo)

        vm.onAction(AdminGenresAction.OnAddClick)
        vm.onAction(AdminGenresAction.OnNameChange("Horror"))
        vm.onAction(AdminGenresAction.OnSave)

        assertThat(repo.createGenreCount).isEqualTo(1)
        assertThat(vm.state.value.showAddDialog).isFalse()
        assertThat(vm.state.value.genres).hasSize(2)
    }

    @Test
    fun `a blank name does not submit`() = runTest {
        val repo = FakeAdminCatalogRepository()
        val vm = AdminGenresViewModel(repo)

        vm.onAction(AdminGenresAction.OnAddClick)
        vm.onAction(AdminGenresAction.OnNameChange("   "))
        vm.onAction(AdminGenresAction.OnSave)

        assertThat(repo.createGenreCount).isEqualTo(0)
    }

    @Test
    fun `a delete conflict surfaces an error`() = runTest {
        val repo = FakeAdminCatalogRepository().apply { deleteGenreError = DataError.Network.CONFLICT }
        val vm = AdminGenresViewModel(repo)

        vm.onAction(AdminGenresAction.OnDeleteClick(repo.genres.first()))
        vm.onAction(AdminGenresAction.OnConfirmDelete)

        assertThat(vm.state.value.error).isNotNull()
    }
}
