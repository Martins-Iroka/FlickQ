package com.martdev.flickq.feature.notification.domain

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult

/** Ties this device's push installation id (FID) to the signed-in user on the server. */
interface DeviceRegistrationRepository {
    /** `PUT /devices`: upserts [fid] for the caller and refreshes its `last_seen_at`. */
    suspend fun register(fid: String): EmptyResult<DataError>

    /** `DELETE /devices/{fid}`: detaches [fid] from the caller. */
    suspend fun unregister(fid: String): EmptyResult<DataError>
}