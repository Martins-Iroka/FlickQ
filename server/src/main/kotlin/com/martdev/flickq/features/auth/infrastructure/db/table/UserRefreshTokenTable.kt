package com.martdev.flickq.features.auth.infrastructure.db.table

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object UserRefreshTokenTable : LongIdTable("refresh_tokens") {
    val userId = reference("user_id", UserTable)
        .index("idx_refresh_token_user_id")
    val tokenHash = text("token_hash").uniqueIndex()
        .index("idx_refresh_token_token_hash")
    val expiresAt = datetime("expires_at")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val revoked = bool("revoked").default(false)
}

class UserRefreshTokenEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserRefreshTokenEntity>(UserRefreshTokenTable)

    var userEntity by UserEntity referencedOn UserRefreshTokenTable.userId
    var tokenHash by UserRefreshTokenTable.tokenHash
    var expiryTime by UserRefreshTokenTable.expiresAt
    var revoked by UserRefreshTokenTable.revoked
}