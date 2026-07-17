package com.martdev.flickq.adminkobweb.pages.admin.room

import com.martdev.flickq.feature.admin.presentation.logic.rooms.RoomData
import com.varabyte.kobweb.browser.storage.StorageKey
import kotlinx.serialization.json.Json

class RoomDataStorageKey(name: String) : StorageKey<RoomData>(name) {
    override fun convertToString(value: RoomData): String {
        return Json.encodeToString(value)
    }

    override fun convertFromString(value: String): RoomData? {
        return runCatching { Json.decodeFromString<RoomData>(value) }.getOrNull()
    }
}