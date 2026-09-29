package com.martdev.flickq.features.notification.infrastructure.db.repository

import com.martdev.flickq.features.notification.domain.repository.DeviceTokenRepository
import com.martdev.flickq.features.notification.infrastructure.db.table.DeviceTokenEntity
import com.martdev.flickq.features.notification.infrastructure.db.table.DeviceTokenTable
import com.martdev.flickq.features.notification.infrastructure.db.table.toDeviceToken
import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.shared.domain.model.DataResult
import com.martdev.flickq.shared.infrastruce.db.withSuspendTransaction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsertReturning
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
class DeviceTokenRepositoryImpl : DeviceTokenRepository {
    override suspend fun upsert(
        userId: Long,
        token: String,
        platform: DevicePlatform,
        appVersion: String,
        locale: String
    ): DataResult<DeviceToken> {
        return withSuspendTransaction {
            val d = DeviceTokenTable.upsertReturning(
                DeviceTokenTable.token,
                onUpdate = {
                    it[DeviceTokenTable.userId] = insertValue(DeviceTokenTable.userId)
                    it[DeviceTokenTable.platform] = insertValue(DeviceTokenTable.platform)
                    it[DeviceTokenTable.appVersion] = insertValue(DeviceTokenTable.appVersion)
                    it[DeviceTokenTable.locale] = insertValue(DeviceTokenTable.locale)
                    it[DeviceTokenTable.lastSeenAt] = CurrentTimestamp
                    it[DeviceTokenTable.invalidatedAt] = null
                }
            ) {
                it[DeviceTokenTable.userId] = userId
                it[DeviceTokenTable.token] = token
                it[DeviceTokenTable.platform] = platform
                it[DeviceTokenTable.appVersion] = appVersion
                it[DeviceTokenTable.locale] = locale
            }.single()
                .let { row -> DeviceTokenEntity.wrapRow(row).toDeviceToken() }
            /* val deviceToken =
                 DeviceTokenEntity.find { DeviceTokenTable.token eq token }.single().toDeviceToken()*/

            DataResult.Success(d)
        }
    }

    override suspend fun activeTokensFor(userId: Long): DataResult<List<String>> {
        return withSuspendTransaction {
            val tokens = DeviceTokenTable
                .select(DeviceTokenTable.token)
                .where { (DeviceTokenTable.userId eq userId) and DeviceTokenTable.invalidatedAt.isNull() }
                .orderBy(DeviceTokenTable.lastSeenAt to SortOrder.DESC)
                .limit(MAX_ACTIVE_DEVICES_PER_USER)
                .map { it[DeviceTokenTable.token] }
            DataResult.Success(tokens)
        }
    }

    override suspend fun invalidate(tokens: Collection<String>): DataResult<Int> {
        if (tokens.isEmpty()) return DataResult.Success(0)

        return withSuspendTransaction {
            val updated = tokens.distinct().chunked(IN_LIST_CHUNK)
                .sumOf { chunk ->
                    DeviceTokenTable.update({
                        (DeviceTokenTable.token inList chunk) and DeviceTokenTable.invalidatedAt.isNull()
                    }) {
                        it[DeviceTokenTable.invalidatedAt] = CurrentTimestamp
                    }
                }
            DataResult.Success(updated)
        }
    }

    override suspend fun invalidateForUser(
        userId: Long,
        token: String
    ): DataResult<Boolean> {
        return withSuspendTransaction {
            val updated = DeviceTokenTable.update({
                (DeviceTokenTable.token eq token) and
                        (DeviceTokenTable.userId eq userId) and
                        DeviceTokenTable.invalidatedAt.isNull()
            }) {
                it[DeviceTokenTable.invalidatedAt] = CurrentTimestamp
            }

            DataResult.Success(updated > 0)
        }
    }

    override suspend fun pruneStale(
        staleBefore: Instant,
        invalidatedBefore: Instant,
        limit: Int
    ): DataResult<List<String>> {
        return withSuspendTransaction {
            val isStaleActive = DeviceTokenTable.invalidatedAt.isNull() and
                    (DeviceTokenTable.lastSeenAt less staleBefore)
            val isLongDead = DeviceTokenTable.invalidatedAt.isNotNull() and
                    (DeviceTokenTable.invalidatedAt less invalidatedBefore)

            val victims = DeviceTokenTable
                .select(
                    DeviceTokenTable.id, DeviceTokenTable.token,
                    DeviceTokenTable.invalidatedAt
                )
                .where { isStaleActive or isLongDead }
                .orderBy(DeviceTokenTable.id to SortOrder.ASC)
                .limit(limit)
                .toList()

            if (victims.isEmpty()) return@withSuspendTransaction DataResult.Success(emptyList())

            val ids = victims.map { it[DeviceTokenTable.id] }
            DeviceTokenTable.deleteWhere {
                DeviceTokenTable.id inList ids and (isStaleActive or isLongDead)
            }

            val active = victims.filter {
                it[DeviceTokenTable.invalidatedAt] ==
                        null
            }.map { it[DeviceTokenTable.token] }

            DataResult.Success(active)
        }
    }

    private companion object {
        const val MAX_ACTIVE_DEVICES_PER_USER = 10
        const val IN_LIST_CHUNK = 1000
    }
}