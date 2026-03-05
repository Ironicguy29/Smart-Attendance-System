package com.smartattendance.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Generates and caches a stable device ID used for duplicate / proxy prevention.
 *
 * Primary:  Android ID (unique per app install, resets on factory reset)
 * Fallback: A random UUID persisted in SharedPreferences
 */
object DeviceIdHelper {

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return androidId
        }
        // Fallback: generate once and persist
        val prefs = context.getSharedPreferences(Constants.PREF_FILE, Context.MODE_PRIVATE)
        var storedId = prefs.getString(Constants.PREF_DEVICE_ID, null)
        if (storedId == null) {
            storedId = UUID.randomUUID().toString()
            prefs.edit().putString(Constants.PREF_DEVICE_ID, storedId).apply()
        }
        return storedId
    }
}
