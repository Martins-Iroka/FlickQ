package com.martdev.flickq.feature.auth.data

import com.martdev.flickq.feature.auth.domain.AuthRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val authDataModule = module {
    singleOf(::FakeAuthDataSource) { bind<AuthRepository>() }
}
