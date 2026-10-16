package com.martdev.flickq.feature.notification.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.notification.domain.DeviceRegistrationRepository

/** No-op registration, used on fakes and on platforms the server has no push platform for. */
class FakeDeviceRegistrationDataSource : DeviceRegistrationRepository {
    override suspend fun register(fid: String): EmptyResult<DataError> = Result.Success(Unit)

    override suspend fun unregister(fid: String): EmptyResult<DataError> = Result.Success(Unit)
}