package com.martdev.flickq.core.data

import com.russhwolf.settings.Settings

/**
 * [TokenStorage] backed by a multiplatform-settings [Settings]. On iOS the settings are
 * over the Keychain; on Android over EncryptedSharedPreferences (Keystore).
 */
internal class SettingsTokenStorage(private val settings: Settings) : TokenStorage {

    override suspend fun getAccessToken(): String? = settings.getStringOrNull(KEY_ACCESS)

    override suspend fun getRefreshToken(): String? = settings.getStringOrNull(KEY_REFRESH)

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        settings.putString(KEY_ACCESS, accessToken)
        settings.putString(KEY_REFRESH, refreshToken)
    }

    override suspend fun clear() {
        settings.remove(KEY_ACCESS)
        settings.remove(KEY_REFRESH)
    }

    private companion object {
        const val KEY_ACCESS = "flickq.access_token"
        const val KEY_REFRESH = "flickq.refresh_token"
    }
}
