package com.martdev.flickq.features.notification.infrastructure.db.table

import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.shared.infrastruce.db.setEnumeration
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object DeviceTokenTable : LongIdTable("device_tokens") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val token = text("token").uniqueIndex()
    val platform = setEnumeration<DevicePlatform>("platform", "device_platform")
    val appVersion = varchar("app_version", 32)
    val locale = varchar("locale", 35)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val lastSeenAt = timestamp("last_seen_at").defaultExpression(CurrentTimestamp)
    val invalidatedAt = timestamp("invalidated_at").nullable()
}

class DeviceTokenEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<DeviceTokenEntity>(DeviceTokenTable)

    var userId by DeviceTokenTable.userId
    var token by DeviceTokenTable.token
    var platform by DeviceTokenTable.platform
    var appVersion by DeviceTokenTable.appVersion
    var locale by DeviceTokenTable.locale
    var createdAt by DeviceTokenTable.createdAt
    var lastSeenAt by DeviceTokenTable.lastSeenAt
    var invalidatedAt by DeviceTokenTable.invalidatedAt
}

fun DeviceTokenEntity.toDeviceToken() = DeviceToken(
    id = id.value,
    userId.value,
    token,
    platform,
    appVersion,
    locale,
    createdAt,
    lastSeenAt,
    invalidatedAt
)