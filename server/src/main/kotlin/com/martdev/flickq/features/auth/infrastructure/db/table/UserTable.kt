package com.martdev.flickq.features.auth.infrastructure.db.table

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.shared.infrastruce.db.setEnumeration
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object UserTable : LongIdTable("users") {
    val email = citext("email").uniqueIndex()
    val password = text("password")
    val isVerified = bool("is_verified").default(false)
    val role = setEnumeration<Role>("role", "role_data").default(Role.USER)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

class UserEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserEntity>(UserTable)

    var email by UserTable.email
    var password by UserTable.password
    var isVerified by UserTable.isVerified
    var role by UserTable.role
}

fun UserEntity.toUserModel() = UserData(
    id.value,
    email,
    password,
    isVerified,
    role
)