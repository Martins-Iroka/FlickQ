package com.martdev.flickq.adminkobweb.pages.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.martdev.flickq.adminkobweb.theme.montserrat
import com.martdev.flickq.core.presentation.UiText
import com.martdev.flickq.feature.admin.presentation.logic.login.AdminLoginAction
import com.martdev.flickq.feature.admin.presentation.logic.login.AdminLoginEvent
import com.martdev.flickq.feature.admin.presentation.logic.login.AdminLoginViewModel
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.lineHeight
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.PageContext
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.icons.fa.FaClapperboard
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.sessionStorage
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh

private fun UiText.plain(): String = when (this) {
    is UiText.DynamicString -> value
}

@Page("login")
@Composable
fun LoginPage(ctx: PageContext) {
    val vm = rememberAdminViewModel<AdminLoginViewModel>()
    val state by vm.state.collectAsState()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is AdminLoginEvent.Authenticated -> {
                    sessionStorage.setItem("exp", event.exp)
                    ctx.router.navigateTo("/admin/dashboard")
                }
            }
        }
    }

    // Two-column shell: a cinematic hero on the left (hidden on narrow viewports) and the auth
    // form on the right.
    Row(modifier = Modifier.fillMaxWidth().minHeight(100.vh).backgroundColor(AdminColors.Bg)) {
        HeroPanel()
        Box(
            modifier = Modifier
                .flexGrow(1)
                .minHeight(100.vh)
                .backgroundColor(AdminColors.Bg)
                .padding(topBottom = 48.px, leftRight = 24.px),
            contentAlignment = Alignment.Center,
        ) {
            LoginForm(
                email = state.email,
                password = state.password,
                error = state.error?.plain(),
                canSubmit = state.canSubmit,
                isLoading = state.isLoading,
                onEmail = { vm.onAction(AdminLoginAction.OnEmailChange(it)) },
                onPassword = { vm.onAction(AdminLoginAction.OnPasswordChange(it)) },
                onSubmit = { vm.onAction(AdminLoginAction.OnSubmit) },
            )
        }
    }
}

@Composable
private fun HeroPanel() {
    Column(
        modifier = Modifier
            .flexGrow(1)
            .minHeight(100.vh)
            .styleModifier { property("background", "linear-gradient(135deg, #010f1f 0%, #051424 60%, #122131 100%)") }
            .padding(64.px),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.px),
            modifier = Modifier.margin(bottom = 28.px),
        ) {
            FaClapperboard(Modifier.color(AdminColors.Primary).fontSize(2.cssRem))
            SpanText(
                "FlickQ",
                Modifier.montserrat().color(AdminColors.Heading).fontSize(2.cssRem).fontWeight(FontWeight.Bold),
            )
        }
        SpanText(
            "Command your cinema.",
            Modifier.montserrat().color(AdminColors.Heading).fontSize(3.cssRem).fontWeight(FontWeight.Bold)
                .lineHeight(1.1).maxWidth(520.px),
        )
        SpanText(
            "Programme showtimes, manage rooms, and watch revenue in real time — all from one secure terminal.",
            Modifier.color(AdminColors.Body).fontSize(1.1.cssRem).lineHeight(1.6).maxWidth(460.px)
                .margin(top = 20.px),
        )
    }
}

@Composable
private fun LoginForm(
    email: String,
    password: String,
    error: String?,
    canSubmit: Boolean,
    isLoading: Boolean,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().maxWidth(420.px),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.px),
    ) {
        // Branding header.
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.px)) {
            Box(
                modifier = Modifier
                    .size(64.px)
                    .backgroundColor(AdminColors.SurfaceAlt)
                    .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                    .borderRadius(16.px),
                contentAlignment = Alignment.Center,
            ) {
                FaClapperboard(Modifier.color(AdminColors.Primary).fontSize(1.6.cssRem))
            }
            SpanText(
                "Admin Login",
                Modifier.montserrat().color(AdminColors.Heading).fontSize(2.cssRem).fontWeight(FontWeight.Bold),
            )
            /*Row(
                modifier = Modifier
                    .backgroundColor(AdminColors.Surface)
                    .border(1.px, LineStyle.Solid, AdminColors.BorderWarm)
                    .borderRadius(9999.px)
                    .padding(topBottom = 5.px, leftRight = 13.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.px),
            ) {
                Box(Modifier.size(8.px).backgroundColor(AdminColors.Amber).borderRadius(9999.px))
                SpanText(
                    "STAFF TERMINAL SECURE",
                    Modifier.color(AdminColors.Body).fontSize(0.75.cssRem).letterSpacing(0.6.px),
                )
            }*/
        }

        // Form fields.
        FieldLabel("Email")
        TextInput(
            text = email,
            onTextChange = onEmail,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "e.g. jdoe@flickq.com",
        )

        FieldLabel("Password")
        TextInput(
            text = password,
            onTextChange = onPassword,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "••••••••••••",
            password = true,
        )

        /*SpanText(
            "Forgot password?",
            Modifier.fillMaxWidth().color(AdminColors.BodyStrong).fontSize(0.8.cssRem)
                .textAlign(TextAlign.End).cursor(Cursor.Pointer),
        )*/

        error?.let {
            SpanText(it, Modifier.color(AdminColors.Primary).fontSize(0.85.cssRem).textAlign(TextAlign.Center))
        }

        Button(
            onClick = { onSubmit() },
            modifier = Modifier
                .fillMaxWidth()
                .backgroundColor(AdminColors.Primary)
                .color(AdminColors.OnPrimary)
                .borderRadius(8.px)
                .padding(topBottom = 16.px)
                .cursor(Cursor.Pointer),
            enabled = canSubmit,
        ) {
            SpanText(if (isLoading) "Signing in…" else "Sign in", Modifier.fontWeight(FontWeight.Bold))
        }

        SpanText(
            "Restricted Access Area · © 2026 FlickQ Systems",
            Modifier.color(AdminColors.Muted).fontSize(0.75.cssRem).textAlign(TextAlign.Center).margin(top = 8.px),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    SpanText(
        text,
        Modifier.fillMaxWidth().color(AdminColors.Heading).fontSize(0.875.cssRem).fontWeight(FontWeight.SemiBold),
    )
}
