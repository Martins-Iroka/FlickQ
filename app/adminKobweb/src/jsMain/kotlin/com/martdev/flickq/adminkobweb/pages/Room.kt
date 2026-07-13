package com.martdev.flickq.adminkobweb.pages

import androidx.compose.runtime.Composable
import com.martdev.flickq.adminkobweb.components.AdminLayout
import com.martdev.flickq.adminkobweb.components.AdminNav
import com.martdev.flickq.adminkobweb.components.RequireAdmin
import com.varabyte.kobweb.core.Page

@Page
@Composable
fun RoomPage() {
    RequireAdmin {
        AdminLayout(AdminNav.Rooms, title = "Rooms") {

        }
    }
}

@Composable
private fun RoomContent() {

}