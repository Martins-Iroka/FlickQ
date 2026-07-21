package com.martdev.flickq.adminkobweb

import androidx.compose.runtime.Composable
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.core.data.coreDataModule
import com.martdev.flickq.core.data.platformDataModule
import com.martdev.flickq.feature.admin.data.adminDataModule
import com.martdev.flickq.feature.admin.presentation.logic.adminPresentationLogicModule
import com.martdev.flickq.feature.auth.data.authDataModule
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fontFamily
import com.varabyte.kobweb.core.App
import com.varabyte.kobweb.core.init.InitKobweb
import com.varabyte.kobweb.core.init.InitKobwebContext
import com.varabyte.kobweb.silk.SilkApp
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.init.registerStyleBase
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
                adminDataModule,
                adminPresentationLogicModule,
            )
        }
        /*ctx.router.addRouteInterceptor {
            val tokenStorage = KoinPlatform.getKoin().get<TokenStorage>()
            val sm = KoinPlatform.getKoin().get<SessionManager>()
            val clock = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val lexp = tokenStorage.getExpiryDate().run {
                Instant.parseOrNull(this)?.toLocalDateTime(TimeZone.UTC)
            }
            val isExpired = lexp != null && lexp < clock
            if (isExpired) {
                "/admin/login?redirect=$path"
            } else {
                path
            }
        }*/
    }
}

/** Dark-first CineAdmin palette + default Inter body type on the deep-navy background. */
@InitSilk
fun initSilk(ctx: InitSilkContext) {
    ctx.config.initialColorMode = ColorMode.DARK
    ctx.stylesheet.registerStyleBase("body") {
        Modifier
            .fontFamily("Inter", "system-ui", "sans-serif")
            .backgroundColor(AdminColors.Bg)
            .color(AdminColors.Body)
    }
}

@App
@Composable
fun AdminApp(content: @Composable () -> Unit) {
    SilkApp {
        content()
    }
}
