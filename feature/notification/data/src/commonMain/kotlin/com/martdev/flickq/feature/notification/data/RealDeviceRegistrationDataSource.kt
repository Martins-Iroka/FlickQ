package com.martdev.flickq.feature.notification.data

import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.data.deleteForStatus
import com.martdev.flickq.core.data.push.PushRoutes
import com.martdev.flickq.core.data.putForStatus
import com.martdev.flickq.feature.notification.domain.DeviceRegistrationRepository
import com.martdev.flickq.notification.model.DevicePlatform
import com.martdev.flickq.notification.request.RegisterDeviceRequestDTO
import io.ktor.client.HttpClient

/**
 * Ktor-backed [DeviceRegistrationRepository] over `PUT /devices` and `DELETE /devices/{fid}`
 * (both 204). The DTO's `token` field carries the FID. Used when
 * [com.martdev.flickq.core.data.AppConfig.USE_FAKES] is false on a platform the server knows.
 */
class RealDeviceRegistrationDataSource(
    private val client: HttpClient,
    private val platform: DevicePlatform,
    private val appInfo: AppInfo,
) : DeviceRegistrationRepository {

    override suspend fun register(fid: String): EmptyResult<DataError> =
        client.putForStatus(
            PushRoutes.DEVICES,
            RegisterDeviceRequestDTO(
                token = fid,
                platform = platform.name,
                appVersion = appInfo.appVersion,
                locale = appInfo.locale,
            )
        )

    override suspend fun unregister(fid: String): EmptyResult<DataError> =
        client.deleteForStatus(PushRoutes.device(fid))
}