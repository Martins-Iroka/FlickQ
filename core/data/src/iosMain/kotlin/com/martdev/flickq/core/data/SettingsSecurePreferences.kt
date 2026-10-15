package com.martdev.flickq.core.data

import com.martdev.flickq.core.data.push.SecurePreferences
import com.russhwolf.settings.Settings

/** [SecurePreferences] over a multiplatform-settings [Settings] (Keystore- or Keychain-backed). */
internal class SettingsSecurePreferences(private val settings: Settings) : SecurePreferences {

    override fun getString(key: String): String? = settings.getStringOrNull(key)

    override fun putString(key: String, value: String) {
        settings.putString(key, value)
    }

    override fun remove(key: String) {
        settings.remove(key)
    }
}