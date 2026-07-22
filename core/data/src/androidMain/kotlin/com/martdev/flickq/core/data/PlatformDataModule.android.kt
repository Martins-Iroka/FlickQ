package com.martdev.flickq.core.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

private const val SECURE_PREFS_NAME = "flickq_secure_prefs"
private const val TAG = "PlatformDataModule"

actual fun httpClientEngine(): HttpClientEngine = OkHttp.create()

actual fun platformDataModule(): Module = module {
    single<TokenStorage> { resolveTokenStorage(androidContext()) }
}

private fun resolveTokenStorage(context: Context): TokenStorage {
    try {
        return SettingsTokenStorage(createEncryptedSettings(context))
    } catch (e: Exception) {
        Log.w(TAG, "Encrypted token storage unreadable, clearing and retrying once", e)
    }

    context.deleteSharedPreferences(SECURE_PREFS_NAME)

    return try {
        SettingsTokenStorage(createEncryptedSettings(context))
    } catch (e: Exception) {
        Log.e(
            TAG,
            "Encrypted token storage unavailable after retry — falling back to in-memory " +
                    "storage. Tokens won't survive a process restart until this device's Keystore recovers.",
            e,
        )
        InMemoryTokenStorage()
    }
}

private fun createEncryptedSettings(context: Context): Settings {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    val prefs = EncryptedSharedPreferences.create(
        context,
        "flickq_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    return SharedPreferencesSettings(prefs)
}
