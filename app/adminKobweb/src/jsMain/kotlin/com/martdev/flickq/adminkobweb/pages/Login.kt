package com.martdev.flickq.adminkobweb.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.martdev.flickq.adminkobweb.koin.rememberAdminViewModel
import com.martdev.flickq.adminkobweb.theme.AdminColors
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
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.letterSpacing
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh

private fun UiText.plain(): String = when (this) {
    is UiText.DynamicString -> value
}

@Page
@Composable
fun LoginPage() {
    val ctx = rememberPageContext()
    val vm = rememberAdminViewModel<AdminLoginViewModel>()
    val state by vm.state.collectAsState()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                AdminLoginEvent.Authenticated -> ctx.router.navigateTo("/")
            }
        }
    }

    // Centered login card on the deep-navy background (the Figma hero split is added later).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .minHeight(100.vh)
            .backgroundColor(AdminColors.Bg)
            .padding(topBottom = 48.px, leftRight = 24.px),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().maxWidth(420.px),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.px),
        ) {
            // Branding header.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.px)) {
                Box(
                    modifier = Modifier
                        .width(64.px)
                        .backgroundColor(AdminColors.SurfaceAlt)
                        .border(1.px, org.jetbrains.compose.web.css.LineStyle.Solid, AdminColors.BorderWarm)
                        .borderRadius(16.px)
                        .padding(16.px),
                    contentAlignment = Alignment.Center,
                ) {
                    SpanText("🎬", Modifier.fontSize(1.5.cssRem))
                }
                SpanText(
                    "CineAdmin",
                    Modifier.color(AdminColors.Heading).fontSize(3.cssRem).fontWeight(FontWeight.Bold),
                )
                Row(
                    modifier = Modifier
                        .backgroundColor(AdminColors.Surface)
                        .border(1.px, org.jetbrains.compose.web.css.LineStyle.Solid, AdminColors.BorderWarm)
                        .borderRadius(9999.px)
                        .padding(topBottom = 5.px, leftRight = 13.px),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.px),
                ) {
                    Box(Modifier.width(8.px).minHeight(8.px).backgroundColor(AdminColors.Amber).borderRadius(9999.px))
                    SpanText(
                        "STAFF TERMINAL SECURE",
                        Modifier.color(AdminColors.Body).fontSize(0.75.cssRem).letterSpacing(0.6.px),
                    )
                }
            }

            // Form.
            FieldLabel("Staff ID / Email")
            TextInput(
                text = state.email,
                onTextChange = { vm.onAction(AdminLoginAction.OnEmailChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "e.g. jdoe@cineadmin.corp",
            )

            FieldLabel("Authentication Key")
            TextInput(
                text = state.password,
                onTextChange = { vm.onAction(AdminLoginAction.OnPasswordChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "••••••••••••",
                password = true,
            )

            state.error?.let {
                SpanText(it.plain(), Modifier.color(AdminColors.Primary).fontSize(0.85.cssRem).textAlign(TextAlign.Center))
            }

            Button(
                onClick = { vm.onAction(AdminLoginAction.OnSubmit) },
                modifier = Modifier
                    .fillMaxWidth()
                    .backgroundColor(AdminColors.Primary)
                    .color(AdminColors.OnPrimary)
                    .borderRadius(8.px)
                    .padding(topBottom = 16.px)
                    .cursor(Cursor.Pointer),
                enabled = state.canSubmit,
            ) {
                SpanText(if (state.isLoading) "Signing in…" else "Initialize Session", Modifier.fontWeight(FontWeight.Bold))
            }

            SpanText(
                "Restricted Access Area · © 2024 CineAdmin Systems",
                Modifier.color(AdminColors.Muted).fontSize(0.75.cssRem).textAlign(TextAlign.Center).margin(top = 8.px),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    SpanText(
        text,
        Modifier.fillMaxWidth().color(AdminColors.Heading).fontSize(0.875.cssRem).fontWeight(FontWeight.SemiBold),
    )
}
