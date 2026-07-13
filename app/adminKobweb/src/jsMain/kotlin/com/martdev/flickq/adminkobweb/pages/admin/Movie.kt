package com.martdev.flickq.adminkobweb.pages.admin

import androidx.compose.runtime.Composable
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.varabyte.kobweb.core.Page

@Page
@Composable
fun MoviePage() {
    RequireAdmin {
        AdminLayout(selected = AdminNav.Movies, title = "Movies") {

        }
    }
}

@Composable
private fun MovieContent() {

}