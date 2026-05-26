package com.martdev.flickq.features.auth.infrastructure.db.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType

class CITextColumnType : TextColumnType() {
    override fun sqlType(): String {
        return "CITEXT"
    }
}

fun Table.citext(name: String) = registerColumn(name, CITextColumnType())