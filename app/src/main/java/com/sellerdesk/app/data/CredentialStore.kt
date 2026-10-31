package com.sellerdesk.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Client ID / secret live in EncryptedSharedPreferences (Android Keystore-backed).
 * Everything else (seen order ids, settings) is non-secret and lives in plain prefs.
 */
class CredentialStore(context: Context) {
    private val app = context.applicationContext
    private val secure: SharedPreferences = createSecure()
    private val state: SharedPreferences = app.getSharedPreferences("state", Context.MODE_PRIVATE)

    private fun createSecure(): SharedPreferences {
        fun build(): SharedPreferences {
            val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                app, "secure_v1", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return try {
            build()
        } catch (e: Exception) {
            // Keystore got reset (e.g. restored device): wipe and start clean.
            app.deleteSharedPreferences("secure_v1")
            build()
        }
    }

    val clientId: String get() = secure.getString("cid", "").orEmpty()
    val clientSecret: String get() = secure.getString("csec", "").orEmpty()
    val isLoggedIn: Boolean get() = clientId.isNotEmpty() && clientSecret.isNotEmpty()

    fun saveCredentials(id: String, secret: String) {
        secure.edit().putString("cid", id).putString("csec", secret).apply()
    }

    fun clearAll() {
        secure.edit().clear().apply()
        state.edit().clear().apply()
    }

    // --- non-secret state ---
    var seen: Set<String>
        get() = state.getStringSet("seen", emptySet()).orEmpty()
        set(v) {
            val trimmed = if (v.size > 500) v.toList().takeLast(500).toSet() else v
            state.edit().putStringSet("seen", trimmed).apply()
        }

    var baselineDone: Boolean
        get() = state.getBoolean("baseline", false)
        set(v) = state.edit().putBoolean("baseline", v).apply()

    /** Master switch for background watching + alarm. */
    var alertsEnabled: Boolean
        get() = state.getBoolean("alerts", true)
        set(v) = state.edit().putBoolean("alerts", v).apply()

    /** Loud mode = looping alarm sound + vibration until you tap Stop. */
    var loud: Boolean
        get() = state.getBoolean("loud", true)
        set(v) = state.edit().putBoolean("loud", v).apply()

    var pollSeconds: Int
        get() = state.getInt("poll", 20)
        set(v) = state.edit().putInt("poll", v.coerceIn(10, 120)).apply()

    var pending: Boolean
        get() = state.getBoolean("pending", false)
        set(v) = state.edit().putBoolean("pending", v).apply()

    var pendingText: String
        get() = state.getString("pendingText", "").orEmpty()
        set(v) = state.edit().putString("pendingText", v).apply()

    var lastAlertAt: Long
        get() = state.getLong("lastAlertAt", 0L)
        set(v) = state.edit().putLong("lastAlertAt", v).apply()

    /** Last API call summary (never contains the secret). Shown in Settings. */
    var lastDiag: String
        get() = state.getString("diag", "No calls yet.").orEmpty()
        set(v) = state.edit().putString("diag", v).apply()
}
