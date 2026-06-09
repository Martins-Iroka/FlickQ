package com.martdev.flickq.adminkobweb

import androidx.compose.runtime.Composable
import com.martdev.flickq.core.data.coreDataModule
import com.martdev.flickq.core.data.platformDataModule
import com.martdev.flickq.feature.admin.presentation.logic.adminPresentationLogicModule
import com.martdev.flickq.feature.auth.data.authDataModule
import com.varabyte.kobweb.core.App
import com.varabyte.kobweb.core.init.InitKobweb
import com.varabyte.kobweb.core.init.InitKobwebContext
import com.varabyte.kobweb.silk.SilkApp
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/** Boots Koin once, with ONLY the data + auth + admin-logic modules (no customer graphs). */
@InitKobweb
fun initKobweb(@Suppress("UNUSED_PARAMETER") ctx: InitKobwebContext) {
    if (GlobalContext.getOrNull() == null) {
        startKoin {
            modules(
                platformDataModule(),
                coreDataModule,
                authDataModule,
                adminPresentationLogicModule,
            )
        }
    }
}

/** Dark-first CineAdmin palette. */
@InitSilk
fun initSilk(ctx: InitSilkContext) {
    ctx.config.initialColorMode = ColorMode.DARK
}

@App
@Composable
fun AdminApp(content: @Composable () -> Unit) {
    SilkApp {
        content()
    }
}
