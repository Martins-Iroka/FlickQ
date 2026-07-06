package com.martdev.flickq.adminkobweb.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.core.data.JwtDecoder
import com.martdev.flickq.core.data.SessionManager
import com.martdev.flickq.core.data.TokenStorage
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.sessionStorage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.web.css.vh
import org.koin.mp.KoinPlatform
import kotlin.time.Clock
import kotlin.time.Instant

private enum class AuthStatus { Checking, Authorized }

/**
 * Gates an admin page behind a valid ADMIN access token. While the (suspending) token check runs
 * we render a neutral placeholder; if the token is missing or non-admin we redirect to `/login`
 * and render nothing. Once authorized the [content] is shown.
 *
 * Independently, we observe [SessionManager.events] for the whole time the page is mounted — a
 * refresh-token expiry or an explicit logout both bounce the user back to `/login`, mirroring the
 * legacy Compose-MP admin app's root `ObserveAsEvents` behaviour.
 *
 * The signature claim isn't verified client-side (the server's `withRole` guard is the real
 * authority); decoding `isAdmin` only decides what UI to offer.
 */
@Composable
fun RequireAdmin(content: @Composable () -> Unit) {
    val ctx = rememberPageContext()
    val tokenStorage = remember { KoinPlatform.getKoin().get<TokenStorage>() }
    val sm = remember { KoinPlatform.getKoin().get<SessionManager>() }
    var status by remember { mutableStateOf(AuthStatus.Checking) }

    LaunchedEffect(Unit) {
        val claims = JwtDecoder.decode(tokenStorage.getAccessToken())
        val clock = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val lexp = sessionStorage.getItem("exp")?.let {
            Instant.parseOrNull(it)?.toLocalDateTime(TimeZone.UTC)
        }
        val isExpired = lexp != null && lexp < clock
        if (claims?.isAdmin == true && isExpired.not()) {
            status = AuthStatus.Authorized
        } else {
            ctx.router.navigateTo("/login")
        }
    }

    LaunchedEffect(Unit) {
        sm.events.collect {
            ctx.router.navigateTo("/login")
        }
    }

    when (status) {
        AuthStatus.Authorized -> content()
        AuthStatus.Checking -> Box(
            modifier = Modifier.fillMaxWidth().minHeight(100.vh).backgroundColor(AdminColors.Bg),
            contentAlignment = Alignment.Center,
        ) {
            SpanText("Authorizing…", Modifier.color(AdminColors.Muted))
        }
    }
}
