package com.martdev.flickq.core.data

import org.koin.dsl.module

val coreDataModule = module {
    single<TokenStorage> { InMemoryTokenStorage() }
}
