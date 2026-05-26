package com.martdev.flickq.features.auth.infrastructure.db.repository

import com.martdev.flickq.auth.model.Role
import com.martdev.flickq.auth.model.UserData
import com.martdev.flickq.features.auth.domain.repository.UserRepository
import com.martdev.flickq.features.auth.infrastructure.db.table.UserEntity
import com.martdev.flickq.features.auth.infrastructure.db.table.UserRefreshTokenEntity
import com.martdev.flickq.features.auth.infrastructure.db.table.UserRefreshTokenTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserTable
import com.martdev.flickq.features.auth.infrastructure.db.table.UserVerificationEntity
import com.martdev.flickq.features.auth.infrastructure.db.table.UserVerificationTable
import com.martdev.flickq.features.auth.infrastructure.db.table.toUserModel
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Single
class UserRepositoryImpl : UserRepository {
    override suspend fun activateUser(token: String): DataResult<Unit> {
        return withSuspendTransaction {
            val userId =
                getUserIdByVerificationToken(token) ?: return@withSuspendTransaction DataResult.Failure.NotFound()

            updateIsVerifiedInUser(userId) ?: return@withSuspendTransaction DataResult.Failure.NotFound()

            val deletedRow = deleteUserVerificationToken(userId)
            if (deletedRow > 0) {
                DataResult.Success(Unit)
            } else DataResult.Failure.UnknownError("Failed to delete user verification token")
        }
    }

    override suspend fun saveUserAndVerificationToken(
        user: UserData,
        token: String
    ): DataResult<UserData> {
        return withSuspendTransaction {
            val userEntity = createUser(user)
            createUserVerificationToken(token, userEntity)
            DataResult.Success(userEntity.toUserModel())
        }
    }

    override suspend fun saveRefreshToken(
        userId: Long,
        tokenHash: String,
        time: LocalDateTime
    ): DataResult<Unit> {
        return withSuspendTransaction {
            val entity = UserEntity.findById(userId) ?: return@withSuspendTransaction DataResult.Failure.NotFound()

            val id = UserRefreshTokenEntity.new {
                userEntity = entity
                this.tokenHash = tokenHash
                expiryTime = time
            }.id
            if (id.value > 0) {
                DataResult.Success(Unit)
            } else DataResult.Failure.UnknownError("Failed to save token")
        }
    }

    override suspend fun rotateRefreshToken(
        oldTokenHash: String,
        newTokenHash: String,
        newExpiry: LocalDateTime,
    ): DataResult<UserData> {
        return withSuspendTransaction {

            val (userId, role) = getUserIdAndRole(oldTokenHash)
                ?: return@withSuspendTransaction DataResult.Failure.NotFound()

            val updatedRows = UserRefreshTokenTable.update({
                (UserRefreshTokenTable.tokenHash eq oldTokenHash) and
                        (UserRefreshTokenTable.revoked eq false)
            }) {
                it[revoked] = true
            }
            if (updatedRows != 1) {
                return@withSuspendTransaction DataResult.Failure.NotFound()
            }

            val entity = UserEntity.findById(userId) ?: return@withSuspendTransaction DataResult.Failure.NotFound()
            UserRefreshTokenEntity.new {
                userEntity = entity
                this.tokenHash = newTokenHash
                expiryTime = newExpiry
            }

            DataResult.Success(UserData(id = userId, role = role))
        }
    }

    override suspend fun deleteExpiredRefreshToken(): DataResult<Unit> {
        return withSuspendTransaction {
            UserRefreshTokenTable.deleteWhere {
                expiresAt less CurrentDateTime
            }
            DataResult.Success(Unit)
        }
    }

    override suspend fun getUserByEmail(email: String): DataResult<UserData> {
        return withSuspendTransaction {
            val entity = UserEntity.find {
                UserTable.email eq email
            }.firstOrNull() ?: return@withSuspendTransaction DataResult.Failure.NotFound()

            DataResult.Success(entity.toUserModel())
        }
    }

    override suspend fun getUserById(userId: Long): DataResult<UserData> {
        return withSuspendTransaction {
            val entity = UserEntity.findById(userId) ?: return@withSuspendTransaction DataResult.Failure.NotFound()

            DataResult.Success(entity.toUserModel())
        }
    }

    override suspend fun getUserIdAndRoleByRefreshToken(tokenHash: String): DataResult<UserData> {
        return withSuspendTransaction {
            val (userId, role) = getUserIdAndRole(tokenHash)
                ?: return@withSuspendTransaction DataResult.Failure.NotFound()

            DataResult.Success(UserData(id = userId, role = role))
        }
    }

    override suspend fun revokeRefreshToken(tokenHash: String): DataResult<Unit> {
        return withSuspendTransaction {
            UserRefreshTokenEntity.findSingleByAndUpdate(
                UserRefreshTokenTable.tokenHash eq tokenHash
            ) {
                it.revoked = true
            } ?: return@withSuspendTransaction DataResult.Failure.NotFound()

            DataResult.Success(Unit)
        }
    }

    override suspend fun deleteAndCreateVerificationToken(token: String, userId: Long): DataResult<Unit> {
        return withSuspendTransaction {
            deleteUserVerificationToken(userId)
            val entity = UserEntity.findById(userId) ?: return@withSuspendTransaction DataResult.Failure.NotFound()
            createUserVerificationToken(token, entity)
            DataResult.Success(Unit)
        }
    }

    override suspend fun deleteUserAndVerificationToken(userId: Long): DataResult<Unit> {
        return withSuspendTransaction {
            val deletedRow = deleteUser(userId)
            if (deletedRow == 0) {
                return@withSuspendTransaction DataResult.Failure.NotFound()
            }
            val deletedVT = deleteUserVerificationToken(userId)
            if (deletedVT == 0) {
                return@withSuspendTransaction DataResult.Failure.NotFound()
            }
            DataResult.Success(Unit)
        }
    }

    private fun getUserIdByVerificationToken(token: String) = UserTable.join(
        otherTable = UserVerificationTable,
        joinType = JoinType.INNER,
        onColumn = UserTable.id,
        otherColumn = UserVerificationTable.userId
    ).select(UserTable.id)
        .where {
            (UserVerificationTable.token eq token) and (UserVerificationTable.expiresAt greater CurrentDateTime)
        }.firstOrNull()?.get(UserTable.id)?.value

    private fun updateIsVerifiedInUser(userId: Long) = UserEntity.findByIdAndUpdate(userId) {
        it.isVerified = true
    }

    private fun deleteUserVerificationToken(userId: Long) = UserVerificationTable.deleteWhere {
        UserVerificationTable.userId eq userId
    }

    private fun createUser(user: UserData) = UserEntity.new {
        email = user.email
        password = user.password
        role = user.role
    }

    private fun createUserVerificationToken(tokenParam: String, uid: UserEntity) {
        val userVerificationId = CompositeID {
            it[UserVerificationTable.token] = tokenParam
        }
        UserVerificationEntity.new(userVerificationId) {
            userId = uid
            expiresAt = Clock.System.now().plus(24.hours).toLocalDateTime(TimeZone.currentSystemDefault())
        }
    }

    private fun deleteUser(userId: Long) = UserTable.deleteWhere {
        UserTable.id eq userId
    }


    private fun getUserIdAndRole(token: String): Pair<Long, Role>? {
        val row = UserTable.join(
            otherTable = UserRefreshTokenTable,
            joinType = JoinType.INNER,
            onColumn = UserTable.id,
            otherColumn = UserRefreshTokenTable.userId
        ).select(UserTable.id, UserTable.role).where {
            (UserRefreshTokenTable.tokenHash eq token) and
                    (UserRefreshTokenTable.expiresAt.greater(CurrentDateTime)) and
                    (UserRefreshTokenTable.revoked eq false)
        }.firstOrNull()

        if (row == null) {
            return null
        }
        val userId = row[UserTable.id].value
        val role = row[UserTable.role]

        return userId to role
    }
}
