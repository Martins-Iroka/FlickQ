package com.martdev.flickq.features.notification.domain.repository

import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.notification.model.DeviceToken
import com.martdev.flickq.shared.domain.model.DataResult
import kotlin.time.Instant

interface DeviceTokenRepository {
    suspend fun upsert(
        userId: Long,
        token: String,
        platform: DevicePlatform,
        appVersion: String,
        locale: String
    ): DataResult<DeviceToken>

    suspend fun activeTokensFor(userId: Long): DataResult<List<String>>

    suspend fun invalidate(tokens: Collection<String>): DataResult<Int>

    suspend fun invalidateForUser(userId: Long, token: String): DataResult<Boolean>

    suspend fun pruneStale(
        staleBefore: Instant,
        invalidatedBefore: Instant,
        limit: Int = 1000
    ): DataResult<PruneResult>
}