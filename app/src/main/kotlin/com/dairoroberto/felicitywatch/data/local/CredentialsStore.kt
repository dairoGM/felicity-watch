package com.dairoroberto.felicitywatch.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "felicity_watch_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var fsolarUsername: String?
        get() = prefs.getString(KEY_FSOLAR_USER, null)
        set(value) = prefs.edit().putString(KEY_FSOLAR_USER, value).apply()

    var fsolarPassword: String?
        get() = prefs.getString(KEY_FSOLAR_PASS, null)
        set(value) = prefs.edit().putString(KEY_FSOLAR_PASS, value).apply()

    var whatsappPhone: String?
        get() = prefs.getString(KEY_WHATSAPP_PHONE, null)
        set(value) = prefs.edit().putString(KEY_WHATSAPP_PHONE, value).apply()

    var callMeBotApiKey: String?
        get() = prefs.getString(KEY_CALLMEBOT_KEY, null)
        set(value) = prefs.edit().putString(KEY_CALLMEBOT_KEY, value).apply()

    fun hasFsolarCredentials(): Boolean = !fsolarUsername.isNullOrBlank() && !fsolarPassword.isNullOrBlank()

    fun hasWhatsappConfig(): Boolean = !whatsappPhone.isNullOrBlank() && !callMeBotApiKey.isNullOrBlank()

    fun clearFsolarCredentials() {
        prefs.edit().remove(KEY_FSOLAR_USER).remove(KEY_FSOLAR_PASS).apply()
    }

    fun clearWhatsappConfig() {
        prefs.edit().remove(KEY_WHATSAPP_PHONE).remove(KEY_CALLMEBOT_KEY).apply()
    }

    /** Cierre de sesión / restablecimiento de fábrica: borra todas las credenciales guardadas. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_FSOLAR_USER = "fsolar_username"
        private const val KEY_FSOLAR_PASS = "fsolar_password"
        private const val KEY_WHATSAPP_PHONE = "whatsapp_phone"
        private const val KEY_CALLMEBOT_KEY = "callmebot_api_key"
    }
}
