package com.martdev.flickq.feature.notification.data

import com.martdev.flickq.core.data.push.DeviceRegistrationStore
import com.martdev.flickq.feature.notification.domain.DeviceRegistrationLedger
import kotlin.time.Instant

/** [DeviceRegistrationLedger] over the core [DeviceRegistrationStore] that logout also clears. */
class StoreBackedRegistrationLedger(
    private val store: DeviceRegistrationStore,
) : DeviceRegistrationLedger {

    override fun lastRegisteredAt(fid: String, userId: String): Instant? =
        store.lastRegisteredAt(fid, userId)

    override fun record(fid: String, userId: String, at: Instant) {
        store.record(fid, userId, at)
    }
}