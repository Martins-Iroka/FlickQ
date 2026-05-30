package com.martdev.flickq.di

import com.martdev.flickq.core.data.coreDataModule
import com.martdev.flickq.feature.auth.data.authDataModule
import com.martdev.flickq.feature.auth.presentation.authPresentationModule
import com.martdev.flickq.feature.movie.data.movieDataModule
import com.martdev.flickq.feature.movie.presentation.moviePresentationModule
import com.martdev.flickq.feature.showtime.data.showtimeDataModule
import com.martdev.flickq.feature.showtime.presentation.showtimePresentationModule
import com.martdev.flickq.feature.booking.data.bookingDataModule
import com.martdev.flickq.feature.booking.presentation.bookingPresentationModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin with every client module. Called once per platform (Android Application,
 * iOS MainViewController, web main). Feature modules are added here as they come online.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(
            coreDataModule,
            authDataModule,
            authPresentationModule,
            movieDataModule,
            moviePresentationModule,
            showtimeDataModule,
            showtimePresentationModule,
            bookingDataModule,
            bookingPresentationModule
        )
    }
}
