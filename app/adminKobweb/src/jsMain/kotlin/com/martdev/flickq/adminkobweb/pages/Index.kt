package com.martdev.flickq.adminkobweb.pages

import androidx.compose.runtime.Composable
import com.martdev.flickq.core.data.AppConfig
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.text.SpanText

@Page
@Composable
fun HomePage() {
    SpanText("FlickQ Admin — Kobweb spike OK. API base: ${AppConfig.BASE_URL}")
}
