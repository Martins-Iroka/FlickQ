package com.martdev.flickq.shared.infrastruce.db

import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

inline fun <reified T : Enum<T>> Table.setEnumeration(name: String, sql: String) = customEnumeration(
    name = name,
    sql = sql,
    fromDb = { value ->
        enumValueOf<T>(value as String)
    },
    toDb = { value ->
        PGobject().apply {
            type = sql
            this.value = value.name
        }
    }
)