package com.martdev.flickq.admin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initAdminKoin()
    ComposeViewport {
        AdminApp()
    }
}
