package com.martdev.flickq.adminkobweb.pages

import androidx.compose.runtime.Composable
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.vh

// Placeholder dashboard landing — the sidebar shell, auth guard, and overview content are
// built next in Phase 1.
@Page
@Composable
fun HomePage() {
    Box(
        modifier = Modifier.fillMaxWidth().minHeight(100.vh).backgroundColor(AdminColors.Bg),
        contentAlignment = Alignment.Center,
    ) {
        SpanText(
            "Cinema Admin — Dashboard (coming soon)",
            Modifier.color(AdminColors.Heading).fontSize(1.5.cssRem).fontWeight(FontWeight.Bold),
        )
    }
}
