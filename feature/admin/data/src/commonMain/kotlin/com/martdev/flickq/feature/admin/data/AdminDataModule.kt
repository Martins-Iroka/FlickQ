package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.admin.domain.AdminReportRepository
import io.ktor.client.HttpClient
import org.koin.dsl.module

val adminDataModule = module {
    if (AppConfig.USE_FAKES) {
        single<AdminReportRepository> { FakeAdminReportDataSource() }
    } else {
        single<AdminReportRepository> { RealAdminReportDataSource(get<HttpClient>()) }
    }
}
