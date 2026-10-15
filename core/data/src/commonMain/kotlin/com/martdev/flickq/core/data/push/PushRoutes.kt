package com.martdev.flickq.core.data.push

import io.ktor.http.encodeURLPathPart

/** Device-registration routes, shared by the notification feature and logout. */
object PushRoutes {
    const val DEVICES = "/devices"

    /** `DELETE /devices/{fid}` with the FID path-encoded. */
    fun device(fid: String): String = "$DEVICES/${fid.encodeURLPathPart()}"

    /** The FID travels in these requests (PUT body, DELETE path), so they are never logged. */
    fun isDeviceRoute(encodedPath: String): Boolean =
        encodedPath.endsWith(DEVICES) || encodedPath.contains("$DEVICES/")
}