package com.martdev.flickq.admin

import com.martdev.flickq.core.data.coreDataModule
import com.martdev.flickq.core.data.platformDataModule
import com.martdev.flickq.feature.admin.data.adminDataModule
import com.martdev.flickq.feature.admin.presentation.adminPresentationModule
import com.martdev.flickq.feature.auth.data.authDataModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin for the admin app with ONLY core data + the shared auth-data binding +
 * the admin modules — the customer feature graphs are never loaded here.
 */
fun initAdminKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(
            platformDataModule(),
            coreDataModule,
            authDataModule,
            adminDataModule,
            adminPresentationModule
        )
    }
}
