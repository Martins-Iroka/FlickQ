package com.martdev.flickq.adminkobweb.pages.admin.showtime

import com.martdev.flickq.feature.admin.presentation.logic.showtimes.ShowtimeData
import com.varabyte.kobweb.browser.storage.StorageKey
import kotlinx.serialization.json.Json

class ShowtimeStorageKey(name: String) : StorageKey<ShowtimeData>(name) {
    override fun convertToString(value: ShowtimeData): String {
        return Json.encodeToString(value)
    }

    override fun convertFromString(value: String): ShowtimeData? {
        return runCatching { Json.decodeFromString<ShowtimeData>(value) }.getOrNull()
    }
}