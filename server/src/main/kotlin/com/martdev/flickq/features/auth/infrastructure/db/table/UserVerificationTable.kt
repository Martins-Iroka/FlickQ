package com.martdev.flickq.features.auth.infrastructure.db.table

import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.CompositeEntity
import org.jetbrains.exposed.v1.dao.CompositeEntityClass
import org.jetbrains.exposed.v1.datetime.datetime

object UserVerificationTable : CompositeIdTable("user_verification_tracking") {
    val token = varchar("token", 255).entityId()
    val userId = reference("user_id", UserTable)
    val expiresAt = datetime("expires_at")

    override val primaryKey: PrimaryKey = PrimaryKey(token)
}

class UserVerificationEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<UserVerificationEntity>(UserVerificationTable)

    var userId by UserEntity.Companion referencedOn UserVerificationTable.userId
    var expiresAt by UserVerificationTable.expiresAt
}