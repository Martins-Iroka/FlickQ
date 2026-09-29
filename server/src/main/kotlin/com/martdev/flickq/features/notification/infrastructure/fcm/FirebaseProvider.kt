package com.martdev.flickq.features.notification.infrastructure.fcm

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.martdev.flickq.config.FcmConfig
import java.io.ByteArrayInputStream
import java.util.Base64

class FirebaseProvider(
    private val config: FcmConfig
) : AutoCloseable {

    private val app: FirebaseApp by lazy {
        FirebaseApp.getApps().firstOrNull {
            it.name == APP_NAME
        } ?: FirebaseApp.initializeApp(buildOptions(), APP_NAME)
    }

    val messaging: FirebaseMessaging by lazy {
        FirebaseMessaging.getInstance(app)
    }

    private fun buildOptions(): FirebaseOptions {
        require(config.credentials.isNotBlank()) {
            "fcm.enabled=true but FCM_SERVICE_ACCOUNT_B64 is not set"
        }
        val json = runCatching {
            Base64.getDecoder().decode(config.credentials.trim())
        }.getOrElse {
            throw IllegalStateException(
                "FCM_SERVICE_ACCOUNT_B64 is not valid base64"
            )
        }

        val credentials = ByteArrayInputStream(json).use {
            GoogleCredentials.fromStream(it)
        }
        return FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(config.projectId)
            .setConnectTimeout(10_000)
            .setReadTimeout(15_000)
            .build()
    }
    override fun close() {
        FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }?.delete()
    }

    private companion object {
        const val APP_NAME = "flickq-server"
    }
}