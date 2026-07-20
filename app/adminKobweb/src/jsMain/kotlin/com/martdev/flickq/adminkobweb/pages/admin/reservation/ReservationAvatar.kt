package com.martdev.flickq.adminkobweb.pages.admin.reservation

import androidx.compose.runtime.Composable
import com.martdev.flickq.adminkobweb.theme.AdminColors
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.px

@Composable
internal fun Avatar(text: String) {
    Box(
        modifier = Modifier.size(26.px).backgroundColor(AdminColors.Chip).borderRadius(9999.px),
        contentAlignment = Alignment.Center,
    ) { SpanText(text, Modifier.color(AdminColors.Muted).fontSize(10.px).fontWeight(FontWeight.SemiBold)) }
}