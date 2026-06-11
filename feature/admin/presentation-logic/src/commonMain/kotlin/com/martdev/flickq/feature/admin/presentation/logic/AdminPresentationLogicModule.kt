package com.martdev.flickq.feature.admin.presentation.logic

import com.martdev.flickq.feature.admin.presentation.logic.hub.AdminHubViewModel
import com.martdev.flickq.feature.admin.presentation.logic.login.AdminLoginViewModel
import com.martdev.flickq.feature.admin.presentation.logic.movies.AdminMoviesViewModel
import com.martdev.flickq.feature.admin.presentation.logic.reports.AdminReportsViewModel
import com.martdev.flickq.feature.admin.presentation.logic.rooms.AdminRoomsViewModel
import com.martdev.flickq.feature.admin.presentation.logic.showtimes.AdminShowtimesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin bindings for the UI-agnostic admin ViewModels. Consumed by BOTH the legacy
 * Compose-Multiplatform admin app (via [adminPresentationModule], which includes this) and the
 * Kobweb admin app (which loads this module directly). ViewModels migrate into here one feature
 * at a time as their Kobweb screens are built.
 */
val adminPresentationLogicModule = module {
    viewModelOf(::AdminLoginViewModel)
    viewModelOf(::AdminHubViewModel)
    viewModelOf(::AdminReportsViewModel)
    viewModelOf(::AdminMoviesViewModel)
    viewModelOf(::AdminRoomsViewModel)
    viewModelOf(::AdminShowtimesViewModel)
}
