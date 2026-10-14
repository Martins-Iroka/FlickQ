package com.martdev.flickq.features.notification.domain.service

import com.martdev.flickq.features.notification.domain.model.OutboxRecord
import com.martdev.flickq.features.notification.domain.repository.NotificationOutboxRepository
import com.martdev.flickq.notification.model.NotificationStatus
import com.martdev.flickq.shared.util.getLoggerFactory
import com.martdev.flickq.shared.util.returnValue
import org.koin.core.annotation.Single
import org.koin.core.annotation.Singleton

interface OutboxAdminService {
    suspend fun list(status: NotificationStatus?, limit: Int, offset: Long): List<OutboxRecord>
    suspend fun retry(id: Long, adminId: Long)
}

@Singleton
class OutboxAdminServiceImpl(
    private val outbox: NotificationOutboxRepository,
) : OutboxAdminService {
    private val log = getLoggerFactory("OutboxAdmin")

    override suspend fun list(status: NotificationStatus?, limit: Int, offset: Long): List<OutboxRecord> =
        outbox.list(status, limit.coerceIn(1, MAX_PAGE), offset).returnValue()

    override suspend fun retry(id: Long, adminId: Long) {
        outbox.requeueDead(id).returnValue()             // NotFound → 404, Conflict → 409 via StatusPages
        log.info("event=outbox.requeue outbox_id={} admin_id={}", id, adminId)   // audit trail
    }

    private companion object {
        const val MAX_PAGE = 100
    }
}