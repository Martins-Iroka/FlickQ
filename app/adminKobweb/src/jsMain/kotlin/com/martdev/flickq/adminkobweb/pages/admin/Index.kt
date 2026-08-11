package com.martdev.flickq.adminkobweb.pages.admin

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.PageContext

@Page("login")
@Composable
fun LoginPage(ctx: PageContext) {
    LoginContent(ctx)
}
