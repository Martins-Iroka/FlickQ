package com.martdev.flickq.feature.auth.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.auth.domain.AuthRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val authDataModule = module {
    if (AppConfig.USE_FAKES) {
        singleOf(::FakeAuthDataSource) { bind<AuthRepository>() }
    } else {
        single<AuthRepository> { RealAuthDataSource(get(), get()) }
    }
}
