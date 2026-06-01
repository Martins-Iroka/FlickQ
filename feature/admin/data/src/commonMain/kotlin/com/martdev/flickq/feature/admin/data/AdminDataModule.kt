package com.martdev.flickq.feature.admin.data

import com.martdev.flickq.core.data.AppConfig
import com.martdev.flickq.feature.admin.domain.AdminCatalogRepository
import com.martdev.flickq.feature.admin.domain.AdminPaymentRepository
import com.martdev.flickq.feature.admin.domain.AdminReportRepository
import com.martdev.flickq.feature.admin.domain.AdminReservationRepository
import io.ktor.client.HttpClient
import org.koin.dsl.module

val adminDataModule = module {
    if (AppConfig.USE_FAKES) {
        single<AdminReportRepository> { FakeAdminReportDataSource() }
        single<AdminCatalogRepository> { FakeAdminCatalogDataSource() }
        single<AdminReservationRepository> { FakeAdminReservationDataSource() }
        single<AdminPaymentRepository> { FakeAdminPaymentDataSource() }
    } else {
        single<AdminReportRepository> { RealAdminReportDataSource(get<HttpClient>()) }
        single<AdminCatalogRepository> { RealAdminCatalogDataSource(get<HttpClient>()) }
        single<AdminReservationRepository> { RealAdminReservationDataSource(get<HttpClient>()) }
        single<AdminPaymentRepository> { RealAdminPaymentDataSource(get<HttpClient>()) }
    }
}
