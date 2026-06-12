package com.martdev.flickq.feature.admin.presentation

import com.martdev.flickq.feature.admin.presentation.genres.AdminGenresViewModel
import com.martdev.flickq.feature.admin.presentation.logic.adminPresentationLogicModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val adminPresentationModule = module {
    // Login + hub + reports + movies + rooms + showtimes + reservations ViewModels now live in
    // :feature:admin:presentation-logic (shared with the Kobweb admin app).
    includes(adminPresentationLogicModule)
    viewModelOf(::AdminGenresViewModel)
}
