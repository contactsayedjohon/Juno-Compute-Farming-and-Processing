package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesHelper(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "juno_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("juno_prefs", Context.MODE_PRIVATE)
    }

    init {
        // Migrate old prefs to encrypted if any exist
        try {
            val oldPrefs = context.getSharedPreferences("juno_prefs", Context.MODE_PRIVATE)
            if (oldPrefs.contains("user_id") && !prefs.contains("user_id")) {
                val editor = prefs.edit()
                oldPrefs.all.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                    }
                }
                editor.apply()
                oldPrefs.edit().clear().apply()
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    var serverUrl: String
        get() = prefs.getString("server_url", "wss://cluster.junoverseai.com/ws/node") ?: "wss://cluster.junoverseai.com/ws/node"
        set(value) = prefs.edit().putString("server_url", value).apply()

    var pairingToken: String
        get() = prefs.getString("pairing_token", "") ?: ""
        set(value) = prefs.edit().putString("pairing_token", value).apply()

    var deviceName: String
        get() {
            if (!prefs.contains("device_name")) {
                prefs.edit().putString("device_name", defaultDeviceName()).apply()
            }
            return prefs.getString("device_name", "") ?: ""
        }
        set(value) = prefs.edit().putString("device_name", value).apply()

    var tags: String
        get() = prefs.getString("tags", "farm-a, gpu-capable") ?: "farm-a, gpu-capable"
        set(value) = prefs.edit().putString("tags", value).apply()

    var isPaired: Boolean
        get() = prefs.getBoolean("is_paired", false)
        set(value) = prefs.edit().putBoolean("is_paired", value).apply()

    var userId: String
        get() = prefs.getString("user_id", "") ?: ""
        set(value) = prefs.edit().putString("user_id", value).apply()

    var authToken: String
        get() = prefs.getString("auth_token", "") ?: ""
        set(value) = prefs.edit().putString("auth_token", value).apply()

    var userEmail: String
        get() = prefs.getString("user_email", "") ?: ""
        set(value) = prefs.edit().putString("user_email", value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean("auto_start_boot", true)
        set(value) = prefs.edit().putBoolean("auto_start_boot", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", false)
        set(value) = prefs.edit().putBoolean("keep_screen_on", value).apply()

    var allowAdbProxy: Boolean
        get() = prefs.getBoolean("allow_adb_proxy", true)
        set(value) = prefs.edit().putBoolean("allow_adb_proxy", value).apply()

    var allowTerminalAccess: Boolean
        get() = prefs.getBoolean("allow_terminal_access", true)
        set(value) = prefs.edit().putBoolean("allow_terminal_access", value).apply()

    var allowFileTransfer: Boolean
        get() = prefs.getBoolean("allow_file_transfer", true)
        set(value) = prefs.edit().putBoolean("allow_file_transfer", value).apply()

    var maxCpuLimit: Float
        get() = prefs.getFloat("max_cpu_limit", 90f)
        set(value) = prefs.edit().putFloat("max_cpu_limit", value).apply()

    var maxTempThreshold: Float
        get() = prefs.getFloat("max_temp_threshold", 42f)
        set(value) = prefs.edit().putFloat("max_temp_threshold", value).apply()

    var chargeOnlyMode: Boolean
        get() = prefs.getBoolean("charge_only_mode", false)
        set(value) = prefs.edit().putBoolean("charge_only_mode", value).apply()

    var allowAccessibility: Boolean
        get() = prefs.getBoolean("allow_accessibility", false)
        set(value) = prefs.edit().putBoolean("allow_accessibility", value).apply()

    var tasksCompletedToday: Int
        get() = prefs.getInt("tasks_completed_today", 0)
        set(value) = prefs.edit().putInt("tasks_completed_today", value).apply()

    private fun defaultDeviceName(): String {
        val model = Build.MODEL
        val rand = (100..999).random()
        return "$model #$rand"
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
